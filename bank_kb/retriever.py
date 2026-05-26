# -*- coding: utf-8 -*-
"""
Hybrid Retriever: BM25 + TF-IDF hybrid retrieval with jieba tokenization.
Builds index from MySQL knowledge_base table on startup.
"""

import re, math
import jieba
from collections import Counter, defaultdict

from config import TOP_K, SIMILARITY_THRESHOLD, CHUNK_SIZE


class HybridRetriever:
    """BM25 + TF-IDF hybrid retriever using jieba Chinese tokenization."""

    def __init__(self):
        self.entries = []          # list of {"id", "question", "answer", "category"}
        self.doc_freq = {}        # term -> doc frequency
        self.doc_count = 0
        self.vocab = set()
        self.idf = {}             # term -> IDF score
        self.doc_tfidf_vectors = []  # list of {term: tfidf_weight}

        # BM25 params
        self.k1 = 1.5
        self.b = 0.75

    # ── Tokenization ───────────────────────────────────────────────

    @staticmethod
    def tokenize(text):
        """Tokenize Chinese text using jieba, returns list of terms."""
        if not text:
            return []
        tokens = jieba.cut_for_search(text)
        # Remove punctuation and whitespace
        tokens = [t.strip() for t in tokens
                  if t.strip() and not re.match(r'^[\s\W]+$', t)]
        return tokens

    # ── Index building ─────────────────────────────────────────────

    def build_index(self, entries):
        """
        Build BM25 + TF-IDF index from knowledge entries.
        entries: list of {"id": int, "question": str, "answer": str, "category": str}
        """
        self.entries = list(entries)
        self.doc_count = len(entries)

        if self.doc_count == 0:
            print("[Retriever] No entries to index.")
            return

        # Collect all tokens per document
        doc_tokens = []
        for entry in entries:
            q_tokens = self.tokenize(entry["question"])
            a_tokens = self.tokenize(entry["answer"])
            # Weight question tokens higher (2x)
            doc_tokens.append(q_tokens * 2 + a_tokens)

        # Compute document frequency
        self.doc_freq = defaultdict(int)
        for tokens in doc_tokens:
            for term in set(tokens):
                self.doc_freq[term] += 1

        # Compute IDF
        self.idf = {}
        for term, df in self.doc_freq.items():
            # IDF = log((N - df + 0.5) / (df + 0.5)) + 1
            self.idf[term] = math.log(
                (self.doc_count - df + 0.5) / (df + 0.5)
            ) + 1

        self.vocab = set(self.doc_freq.keys())

        # Compute TF-IDF vectors
        self.doc_tfidf_vectors = []
        for tokens in doc_tokens:
            tf_map = Counter(tokens)
            total = len(tokens) if tokens else 1
            vec = {term: (freq / total) * self.idf.get(term, 0)
                   for term, freq in tf_map.items()}
            self.doc_tfidf_vectors.append(vec)

        print(f"[Retriever] Indexed {self.doc_count} entries, vocab={len(self.vocab)}")

    # ── Retrieval ─────────────────────────────────────────────────

    def retrieve(self, query, top_k=None):
        """
        Retrieve top-k entries for a query.
        Returns list of (entry, score, method) sorted by hybrid score.
        """
        if top_k is None:
            top_k = TOP_K
        if self.doc_count == 0:
            print("[Retriever.retrieve] doc_count=0, returning empty")
            return []

        q_tokens = self.tokenize(query)
        if not q_tokens:
            print(f"[Retriever.retrieve] q_tokens is empty for query='{query}', returning empty")
            return []

        print(f"[Retriever.retrieve] query='{query}', q_tokens={q_tokens}, doc_count={self.doc_count}")

        # BM25 scores
        bm25_scores = self._bm25_scores(q_tokens, doc_tokens=[
            self.tokenize(e["question"]) * 2 + self.tokenize(e["answer"])
            for e in self.entries
        ])

        # TF-IDF cosine similarity
        tfidf_scores = self._tfidf_cosine_scores(q_tokens)

        # Hybrid: combine BM25 and TF-IDF
        results = []
        for i, entry in enumerate(self.entries):
            bm25 = bm25_scores[i]
            tfidf = tfidf_scores[i]
            # Normalize BM25 to [0, 1]
            max_bm25 = max(bm25_scores) if max(bm25_scores) > 0 else 1
            norm_bm25 = bm25 / max_bm25
            # Weighted hybrid
            hybrid = 0.5 * norm_bm25 + 0.5 * tfidf

            if hybrid >= SIMILARITY_THRESHOLD:
                results.append({
                    "entry": entry,
                    "score": round(hybrid, 4),
                    "bm25": round(bm25, 4),
                    "tfidf": round(tfidf, 4),
                })

        # Sort by hybrid score
        results.sort(key=lambda x: x["score"], reverse=True)
        return results[:top_k]

    def _bm25_scores(self, query_tokens, doc_tokens):
        """Compute BM25 scores for all documents."""
        scores = []
        avgdl = sum(len(d) for d in doc_tokens) / max(len(doc_tokens), 1)

        for doc in doc_tokens:
            score = 0.0
            tf_map = Counter(doc)
            for term in query_tokens:
                if term not in self.idf:
                    continue
                tf = tf_map.get(term, 0)
                idf = self.idf[term]
                numerator = tf * (self.k1 + 1)
                denominator = tf + self.k1 * (1 - self.b + self.b * len(doc) / avgdl)
                score += idf * (numerator / denominator)
            scores.append(score)
        return scores

    def _tfidf_cosine_scores(self, query_tokens):
        """Compute TF-IDF cosine similarity scores."""
        # Query TF-IDF vector
        q_tf = Counter(query_tokens)
        q_total = len(query_tokens) or 1
        q_vec = {term: (freq / q_total) * self.idf.get(term, 0)
                 for term, freq in q_tf.items()}

        # Cosine similarity with each doc
        scores = []
        for doc_vec in self.doc_tfidf_vectors:
            dot = sum(q_vec.get(t, 0) * v for t, v in doc_vec.items())
            q_norm = math.sqrt(sum(v * v for v in q_vec.values()))
            d_norm = math.sqrt(sum(v * v for v in doc_vec.values()))
            if q_norm == 0 or d_norm == 0:
                scores.append(0.0)
            else:
                scores.append(dot / (q_norm * d_norm))
        return scores

    # ── Index refresh ─────────────────────────────────────────────

    def reload(self, entries):
        """Hot-reload the index with new entries."""
        print("[Retriever] Reloading index...")
        self.build_index(entries)
