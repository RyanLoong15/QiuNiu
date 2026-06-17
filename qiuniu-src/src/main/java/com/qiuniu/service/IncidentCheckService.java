package com.qiuniu.service;

import com.qiuniu.model.ProductionIncident;
import com.qiuniu.dao.ProductionIncidentDAO;
import com.qiuniu.dao.IncidentCodeLinkDAO;
import com.qiuniu.model.IncidentCodeLink;
import com.qiuniu.dao.IncidentCheckHistoryDAO;
import com.qiuniu.model.IncidentCheckHistory;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * 事故检核服务
 * 在代码分析/版本比对/上线分析时，扫描历史教训中是否有相似问题，生成告警。
 */
public class IncidentCheckService {

    private ProductionIncidentDAO incidentDAO = new ProductionIncidentDAO();
    private IncidentCodeLinkDAO codeLinkDAO = new IncidentCodeLinkDAO();
    private IncidentCheckHistoryDAO historyDAO = new IncidentCheckHistoryDAO();

    /**
     * 检核代码变更
     *
     * @param codeContent 代码内容或 Diff
     * @param filePaths   相关文件路径列表
     * @param keywords    关键词列表
     * @return 检核结果
     */
    public CheckResult checkCode(String codeContent, List<String> filePaths, List<String> keywords) {
        CheckResult result = new CheckResult();
        // 使用 LinkedHashMap 以 incident id 为 key，避免重复
        Map<Integer, IncidentMatch> matchMap = new LinkedHashMap<>();

        // 1. 关键词匹配（基于 production_incidents.keywords）
        if (keywords != null && !keywords.isEmpty()) {
            String[] kwArray = keywords.stream()
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .toArray(String[]::new);
            if (kwArray.length > 0) {
                List<ProductionIncident> byKeywords = incidentDAO.searchByKeywords(kwArray);
                for (ProductionIncident inc : byKeywords) {
                    String reason = "keyword";
                    IncidentMatch m = createMatch(inc, 0.4f, reason);
                    matchMap.putIfAbsent(inc.getId(), m);
                }
            }
        }

        // 2. 文件路径匹配（基于 incident_code_links.file_path）
        if (filePaths != null && !filePaths.isEmpty()) {
            Set<Integer> matchedIds = new HashSet<>();
            for (String fp : filePaths) {
                if (fp == null || fp.trim().isEmpty()) continue;
                List<IncidentCodeLink> links = codeLinkDAO.searchByFilePath(fp.trim());
                for (IncidentCodeLink link : links) {
                    if (matchedIds.contains(link.getIncidentId())) continue;
                    matchedIds.add(link.getIncidentId());
                    ProductionIncident inc = incidentDAO.getById(link.getIncidentId());
                    if (inc != null) {
                        String reason = String.format("path: %s (L%d-L%d)", 
                            link.getFilePath(), 
                            link.getLineStart() != null ? link.getLineStart() : 0,
                            link.getLineEnd() != null ? link.getLineEnd() : 0);
                        IncidentMatch existing = matchMap.get(inc.getId());
                        if (existing != null) {
                            existing.similarity = Math.min(1.0f, existing.similarity + 0.3f);
                            existing.matchReasons += ", " + reason;
                        } else {
                            matchMap.put(inc.getId(), createMatch(inc, 0.3f, reason));
                        }
                    }
                }
            }
        }

        // 3. 代码片段匹配（基于 incident_code_links.code_snippet / fix_snippet）
        if (codeContent != null && !codeContent.isEmpty()) {
            // 截取前 500 字符进行匹配，避免超长内容
            String searchContent = codeContent.length() > 500 ? codeContent.substring(0, 500) : codeContent;
            List<IncidentCodeLink> snippetLinks = codeLinkDAO.searchByCodeSnippet(searchContent);
            Set<Integer> snippetMatched = new HashSet<>();
            for (IncidentCodeLink link : snippetLinks) {
                if (snippetMatched.contains(link.getIncidentId())) continue;
                snippetMatched.add(link.getIncidentId());
                ProductionIncident inc = incidentDAO.getById(link.getIncidentId());
                if (inc != null) {
                    String reason = String.format("snippet: %s (L%d-L%d)", 
                        link.getFilePath(),
                        link.getLineStart() != null ? link.getLineStart() : 0,
                        link.getLineEnd() != null ? link.getLineEnd() : 0);
                    IncidentMatch existing = matchMap.get(inc.getId());
                    if (existing != null) {
                        existing.similarity = Math.min(1.0f, existing.similarity + 0.3f);
                        existing.matchReasons += ", " + reason;
                    } else {
                        matchMap.put(inc.getId(), createMatch(inc, 0.3f, reason));
                    }
                }
            }
        }

        List<IncidentMatch> matches = new ArrayList<>(matchMap.values());

        // 4. 计算综合相似度并排序
        for (IncidentMatch m : matches) {
            m.similarity = calculateSimilarity(m);
        }
        matches.sort((a, b) -> Float.compare(b.similarity, a.similarity));

        // 5. 过滤：相似度 >= 0.6 或 P1/P2 级别事故
        float threshold = 0.6f;
        List<IncidentMatch> filtered = matches.stream()
            .filter(m -> m.similarity >= threshold || "P1".equals(m.incident.getSeverity()) || "P2".equals(m.incident.getSeverity()))
            .collect(Collectors.toList());

        // 6. 设置结果
        result.hasRisk = !filtered.isEmpty();
        result.matches = filtered;
        result.riskLevel = determineRiskLevel(filtered);
        result.recommendations = generateRecommendations(filtered);

        return result;
    }

    /**
     * 保存检核历史
     */
    public void saveCheckHistory(String checkType, String targetId, CheckResult result) {
        if (!result.hasRisk || result.matches == null) return;

        for (IncidentMatch match : result.matches) {
            IncidentCheckHistory history = new IncidentCheckHistory();
            history.setCheckType(checkType);
            history.setTargetId(targetId);
            history.setIncidentId(match.incident.getId());
            history.setSimilarity(BigDecimal.valueOf(match.similarity));
            historyDAO.insert(history);
        }
    }

    private float calculateSimilarity(IncidentMatch m) {
        return Math.min(m.similarity, 1.0f);
    }

    private String determineRiskLevel(List<IncidentMatch> matches) {
        for (IncidentMatch m : matches) {
            if ("P1".equals(m.incident.getSeverity())) return "P1";
        }
        for (IncidentMatch m : matches) {
            if ("P2".equals(m.incident.getSeverity())) return "P2";
        }
        for (IncidentMatch m : matches) {
            if ("P3".equals(m.incident.getSeverity())) return "P3";
        }
        return matches.isEmpty() ? "none" : "P4";
    }

    private List<String> generateRecommendations(List<IncidentMatch> matches) {
        List<String> recs = new ArrayList<>();
        for (IncidentMatch m : matches) {
            String rec = String.format("【%s】%s - %s\n匹配: %s\n根因: %s\n建议: %s",
                m.incident.getIncidentNo(),
                m.incident.getSeverity(),
                m.incident.getTitle(),
                m.matchReasons,
                truncate(m.incident.getRootCause(), 100),
                truncate(m.incident.getSolution(), 100));
            recs.add(rec);
        }
        return recs;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private IncidentMatch createMatch(ProductionIncident incident, float weight, String reason) {
        IncidentMatch m = new IncidentMatch();
        m.incident = incident;
        m.matchReasons = reason;
        m.similarity = weight;
        return m;
    }

    // ========== 内部类 ==========

    public static class CheckResult {
        public boolean hasRisk;
        public List<IncidentMatch> matches;
        public String riskLevel;
        public List<String> recommendations;
    }

    public static class IncidentMatch {
        public ProductionIncident incident;
        public float similarity;
        public String matchReasons;
    }
}
