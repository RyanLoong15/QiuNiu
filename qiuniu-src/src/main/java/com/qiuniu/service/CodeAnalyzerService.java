package com.qiuniu.service;

import com.qiuniu.dao.CodeAnalysisReportDAO;
import com.qiuniu.dao.GitProjectDAO;
import com.qiuniu.model.CodeAnalysisReport;
import com.qiuniu.model.CodeRuleSet;
import com.qiuniu.util.AIModelClient;
import com.qiuniu.dao.ProductionIncidentDAO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Code Analyzer Service
 * Core engine for full repository code analysis.
 *
 * Design for loose coupling:
 * - analyzeRepo(repoUrl, branch, ...)  : full repo scan entry
 * - analyzeFileList(files, ...)        : diff file analysis entry (called from version-compare)
 * - analyzeFile(content, filename, ...) : single file entry
 *
 * Large file handling:
 * - Files > 60K chars are split into 40K-char chunks with 2K overlap
 * - Each chunk is analyzed separately, then results are combined
 */
public class CodeAnalyzerService {
    private ThreadLocal<String> threadLocalIncidentCheckResult = new ThreadLocal<>();

    private static final Set<String> CODE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "java", "kt", "scala", "groovy",
            "js", "jsx", "ts", "tsx", "mjs", "cjs",
            "py", "rb", "php", "pl", "pm",
            "c", "cpp", "cc", "cxx", "h", "hpp", "m",
            "cs", "go", "rs", "swift",
            "vue", "svelte",
            "sql", "sh", "bash", "zsh",
            "yaml", "yml", "xml", "json", "jsonc",
            "properties", "conf", "cfg", "ini", "toml",
            "md", "txt"));

    private static final Set<String> IGNORED_DIRS = new HashSet<>(Arrays.asList(
            ".git", ".svn", ".hg",
            "node_modules", "bower_components",
            "target", "build", "dist", "out",
            ".idea", ".vscode", ".eclipse",
            ".gradle", ".mvn",
            "vendor", "packages",
            "__pycache__", ".pytest_cache",
            "test", "tests", "Test", "Tests"));

    private static final Set<String> IGNORED_FILES = new HashSet<>(Arrays.asList(
            ".DS_Store", "Thumbs.db", "desktop.ini",
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            ".gitignore", ".gitattributes", ".editorconfig"));

    private static final int MAX_FILE_CHARS = 60000;
    private static final int CHUNK_SIZE = 40000;
    private static final int CHUNK_OVERLAP = 2000;
    private static final int MAX_CONCURRENT = 3;
    private static final int GIT_TIMEOUT = 180;
    private static final long AI_DELAY_MS = 800;

    private final CodeAnalysisReportDAO reportDAO = new CodeAnalysisReportDAO();
    private final GitProjectDAO projectDAO = new GitProjectDAO();
    private final AIModelClient aiClient;
    private final ExecutorService executor;

    public CodeAnalyzerService(AIModelClient aiClient) {
        this.aiClient = aiClient;
        this.executor = Executors.newFixedThreadPool(MAX_CONCURRENT);
    }

    // Public API: analyze by project ID
    public void analyzeRepo(Long projectId, String branch, String ruleSetId,
                            Consumer<ProgressInfo> onProgress) {
        var project = projectDAO.getById(projectId);
        if (project == null) {
            System.err.println("[CodeAnalyzer] Project not found: " + projectId);
            return;
        }
        analyzeRepo(project.getRepoUrl(), branch, project.getName(),
                projectId, project.getTeamId(), ruleSetId, onProgress);
    }

    // Public API: analyze by repo URL
    public void analyzeRepo(String repoUrl, String branch, String projectName,
                            Long projectId, Long teamId, String ruleSetId,
                            Consumer<ProgressInfo> onProgress) {
        long startMs = System.currentTimeMillis();
        boolean isAuto = (ruleSetId == null || "auto".equalsIgnoreCase(ruleSetId));

        CodeAnalysisReport report = new CodeAnalysisReport(projectId, 0L, isAuto ? "auto" : ruleSetId);
        report.setProjectName(projectName);
        report.setTeamId(teamId);
        report.setRepoUrl(repoUrl);
        report.setBranch(branch != null ? branch : "main");
        if (!reportDAO.insert(report)) {
            System.err.println("[CodeAnalyzer] Database insert failed for report, aborting.");
            report.setStatus("FAILED");
            report.setReport("Failed to create report record in database.");
            onProgress.accept(new ProgressInfo(0, 0, null, "Database insert failed"));
            return;
        }
        final Long reportId = report.getId();

        Path workDir = null;
        try {
            String tmpBase = getTmpDir();
            workDir = Files.createTempDirectory(Paths.get(tmpBase), "qn-analyze-");
            String actualBranch = (branch != null && !branch.trim().isEmpty()) ? branch.trim() : "main";

            int exit = runGit(workDir.toFile(), GIT_TIMEOUT,
                    "git", "clone", "--branch", actualBranch, "--depth=1", repoUrl, ".");
            if (exit != 0) {
                String err = "[CLONE_FAILED:" + exit + "]\n" + captureGit(workDir.toFile(), 30,
                        "git", "clone", "--branch", actualBranch, "--depth=1", repoUrl, ".");
                reportDAO.fail(reportId, err);
                onProgress.accept(new ProgressInfo(0, 0, null, "Clone failed: " + exit));
                return;
            }
            onProgress.accept(new ProgressInfo(0, 0, null, "Clone done, scanning files..."));

            List<ScannedFile> allFiles = scanFiles(workDir);

            int totalFiles = allFiles.size();
            reportDAO.updateStatus(reportId, "RUNNING", 0, totalFiles);
            System.out.println("[CodeAnalyzer] Found " + totalFiles + " files, auto=" + isAuto);

            if (totalFiles == 0) {
                String finalReport = "## Analysis Report\n\nNo code files found in the repository.";
                reportDAO.finish(reportId, finalReport, this.getLastIncidentCheckResult(), 0, System.currentTimeMillis() - startMs);
                onProgress.accept(new ProgressInfo(0, 0, null, "No code files found"));
                return;
            }

            List<FileResult> results;
            if (isAuto) {
                // Auto mode: group files by language, use appropriate rule per group
                results = analyzeByAutoGroup(allFiles, (ProgressInfo pi) -> {
                    if (pi.error != null) System.err.println("[CodeAnalyzer] Error: " + pi.error);
                    onProgress.accept(pi);
                    reportDAO.updateStatus(reportId, "RUNNING", pi.analyzed, totalFiles);
                });
            } else {
                // Single rule mode (backward compatible)
                CodeRuleSet ruleSet = CodeRuleSet.getById(ruleSetId);
                if (ruleSet == null) ruleSet = CodeRuleSet.GENERIC_SECURITY();
                if (ruleSet.getFileTypes() != null && !ruleSet.getFileTypes().isEmpty()) {
                    allFiles = filterByRuleSet(allFiles, ruleSet);
                }
                results = analyzeFilesConcurrently(allFiles, ruleSet, (ProgressInfo pi) -> {
                    if (pi.error != null) System.err.println("[CodeAnalyzer] Error: " + pi.error);
                    onProgress.accept(pi);
                    reportDAO.updateStatus(reportId, "RUNNING", pi.analyzed, totalFiles);
                });
            }

            // Summarize
            String ruleName = isAuto ? "Auto (Multi-Rule)" : CodeRuleSet.getById(ruleSetId).getName();
            String summary = summarizeReport(projectName, repoUrl, ruleName, results, startMs, isAuto);
            
            // Append historical incident check
            summary = appendIncidentCheck(summary, results);
            
            int totalIssues = 0;
            for (FileResult r : results) totalIssues += r.issues;
            reportDAO.finish(reportId, summary, this.getLastIncidentCheckResult(), totalIssues, System.currentTimeMillis() - startMs);
            System.out.println("[CodeAnalyzer] Done reportId=" + reportId + " files=" + results.size() + " issues=" + totalIssues);

        } catch (Exception e) {
            e.printStackTrace();
            reportDAO.fail(reportId, "[ERROR] " + e.getMessage());
            onProgress.accept(new ProgressInfo(0, 0, null, "Error: " + e.getMessage()));
        } finally {
            if (workDir != null) deleteDir(workDir.toFile());
        }
    }

    /**
     * Auto mode: scan all files, group by resolved rule set ID, analyze each group
     * with its best-matching rule set.
     */
    private List<FileResult> analyzeByAutoGroup(List<ScannedFile> allFiles,
                                                 Consumer<ProgressInfo> onProgress) {
        Map<String, List<ScannedFile>> groups = new LinkedHashMap<>();
        Map<String, CodeRuleSet> ruleCache = new HashMap<>();

        for (ScannedFile f : allFiles) {
            String ext = getExtension(f.path);
            String rsId = CodeRuleSet.resolveRuleSetId(ext);
            groups.computeIfAbsent(rsId, k -> new ArrayList<>()).add(f);
            ruleCache.computeIfAbsent(rsId, k -> CodeRuleSet.getById(rsId));
        }

        // Log distribution
        StringBuilder dist = new StringBuilder("[CodeAnalyzer] Auto-group: ");
        for (Map.Entry<String, List<ScannedFile>> e : groups.entrySet()) {
            CodeRuleSet rs = ruleCache.get(e.getKey());
            dist.append(rs.getName()).append("(").append(e.getValue().size()).append("), ");
        }
        System.out.println(dist.toString());

        // Analyze each group
        List<FileResult> allResults = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completed = new AtomicInteger(0);
        int totalCount = allFiles.size();

        for (Map.Entry<String, List<ScannedFile>> e : groups.entrySet()) {
            String rsId = e.getKey();
            CodeRuleSet ruleSet = ruleCache.get(rsId);
            List<ScannedFile> groupFiles = e.getValue();

            List<FileResult> groupResults = analyzeFilesConcurrently(groupFiles, ruleSet,
                    (ProgressInfo pi) -> {
                        int done = completed.addAndGet(pi.analyzed - (pi.analyzed > 0 ? 1 : 0));
                        // Recalculate global progress
                        onProgress.accept(new ProgressInfo(completed.get(), totalCount, pi.currentFile, pi.error));
                    });

            // Tag each result with rule name for summary
            for (FileResult r : groupResults) {
                r.ruleName = ruleSet.getName();
                allResults.add(r);
            }
        }

        return allResults;
    }

    // Public API: analyze file list (for diff analysis)
    public String analyzeFileList(List<FileInfo> files, String ruleSetId, String projectName) {
        CodeRuleSet ruleSet = CodeRuleSet.getById(ruleSetId);
        if (files == null || files.isEmpty()) {
            return "## Analysis Report\n\nNo files provided.";
        }
        List<ScannedFile> scanned = new ArrayList<>();
        for (FileInfo fi : files) {
            scanned.add(new ScannedFile(fi.path, fi.content));
        }
        List<FileResult> results = analyzeFilesConcurrently(scanned, ruleSet, null);
        String summary = summarizeReport(projectName, null, ruleSet.getName(), results, System.currentTimeMillis(), false);
        
        // Append historical incident check
        summary = appendIncidentCheck(summary, results);
        
        return summary;
    }

    // Scan directory for code files
    private List<ScannedFile> scanFiles(Path rootDir) throws IOException {
        List<ScannedFile> files = new ArrayList<>();
        scanDir(rootDir, rootDir, files);
        return files;
    }

    private void scanDir(Path root, Path current, List<ScannedFile> files) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry) && !Files.isSymbolicLink(entry)) {
                    if (IGNORED_DIRS.contains(name)) continue;
                    scanDir(root, entry, files);
                } else if (Files.isRegularFile(entry)) {
                    if (IGNORED_FILES.contains(name)) continue;
                    String ext = getExtension(name);
                    if (ext != null && CODE_EXTENSIONS.contains(ext.toLowerCase())) {
                        try {
                            if (Files.size(entry) > 5 * 1024 * 1024) continue;
                            String content = Files.readString(entry, StandardCharsets.UTF_8);
                            String relPath = root.relativize(entry).toString();
                            files.add(new ScannedFile(relPath, content));
                        } catch (Exception e) {
                            // skip unreadable files
                        }
                    }
                }
            }
        }
    }

    private List<ScannedFile> filterByRuleSet(List<ScannedFile> files, CodeRuleSet ruleSet) {
        List<ScannedFile> filtered = new ArrayList<>();
        for (ScannedFile f : files) {
            String ext = getExtension(f.path);
            if (ruleSet.supportsFileType(ext)) filtered.add(f);
        }
        return filtered;
    }

    // Concurrent file analysis
    private List<FileResult> analyzeFilesConcurrently(List<ScannedFile> files,
                                                       CodeRuleSet ruleSet,
                                                       Consumer<ProgressInfo> onProgress) {
        List<FileResult> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completed = new AtomicInteger(0);
        int totalCount = files.size();
        List<Future<FileResult>> futures = new ArrayList<>();

        for (ScannedFile sf : files) {
            final ScannedFile f = sf;
            futures.add(executor.submit(() -> {
                FileResult r = analyzeSingleFile(f.path, f.content, ruleSet);
                int done = completed.incrementAndGet();
                if (onProgress != null) {
                    onProgress.accept(new ProgressInfo(done, totalCount, f.path, null));
                }
                return r;
            }));
        }

        for (Future<FileResult> fu : futures) {
            try {
                results.add(fu.get(5, TimeUnit.MINUTES));
            } catch (Exception e) {
                System.err.println("[CodeAnalyzer] Task exception: " + e.getMessage());
            }
        }
        return results;
    }

    // Analyze single file
    private FileResult analyzeSingleFile(String filePath, String content, CodeRuleSet ruleSet) {
        FileResult result = new FileResult(filePath);
        if (content == null || content.trim().isEmpty()) {
            result.status = "empty";
            return result;
        }
        if (content.length() > MAX_FILE_CHARS) {
            return analyzeLargeFile(filePath, content, ruleSet);
        }
        try {
            String prompt = buildUserPrompt(filePath, content, ruleSet);
            String analysis = aiClient.analyze(ruleSet.getSystemPrompt(), prompt);
            result.status = "ok";
            result.analysis = (analysis != null && !analysis.isEmpty()) ? analysis : "[No response from AI]";
            result.issues = countIssues(result.analysis);
        } catch (Exception e) {
            result.status = "error";
            result.analysis = "[Error] " + e.getMessage();
        }
        return result;
    }

    // Analyze large file with chunking
    private FileResult analyzeLargeFile(String filePath, String content, CodeRuleSet ruleSet) {
        FileResult result = new FileResult(filePath);
        result.status = "chunked";
        List<String> chunks = chunkContent(content);
        StringBuilder combined = new StringBuilder();
        int chunkNum = 0;

        for (String chunk : chunks) {
            chunkNum++;
            if (chunkNum > 20) {
                combined.append("\n[File too large, analyzed first 20 chunks only]\n");
                break;
            }
            try {
                String prompt = buildUserPrompt(filePath + " [Part " + chunkNum + "/" + chunks.size() + "]", chunk, ruleSet);
                String analysis = aiClient.analyze(ruleSet.getSystemPrompt(), prompt);
                if (analysis != null && !analysis.isEmpty()) {
                    combined.append("\n--- Part ").append(chunkNum).append(" ---\n").append(analysis).append("\n");
                }
                Thread.sleep(AI_DELAY_MS / 2);
            } catch (Exception e) {
                combined.append("\n[Chunk ").append(chunkNum).append(" error: ").append(e.getMessage()).append("]\n");
            }
        }
        result.analysis = combined.toString();
        result.issues = countIssues(result.analysis);
        return result;
    }

    private List<String> chunkContent(String content) {
        List<String> chunks = new ArrayList<>();
        int len = content.length();
        int start = 0;
        int idx = 0;
        while (start < len) {
            int end = Math.min(start + CHUNK_SIZE, len);
            if (end < len) {
                int lastNl = content.lastIndexOf('\n', end);
                if (lastNl > start + CHUNK_SIZE / 2) end = lastNl + 1;
            }
            chunks.add(content.substring(start, end));
            idx++;
            if (idx > 100) break; // safety limit
            start = end - CHUNK_OVERLAP;
            if (start >= len) break;
        }
        return chunks;
    }

    private String buildUserPrompt(String filePath, String content, CodeRuleSet ruleSet) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please review the following code deeply.\n\n");
        sb.append("[File] ").append(filePath).append("\n");
        sb.append("[Standard] ").append(ruleSet.getName()).append("\n\n");
        sb.append("[Code]\n```\n");
        if (content.length() > 30000) {
            sb.append(content, 0, 30000);
            sb.append("\n... [truncated, showing first 30000 chars] ...\n");
        } else {
            sb.append(content);
        }
        sb.append("\n```\n\nPlease output analysis result according to the review standard:");
        return sb.toString();
    }

    private int countIssues(String analysis) {
        if (analysis == null || analysis.isEmpty()) return 0;
        int count = 0;
        for (String line : analysis.split("\n")) {
            if (line.contains("\uD83D\uDD34") || line.contains("\uD83D\uDFE1")) count++;
        }
        return count;
    }

    private String summarizeReport(String projectName, String repoUrl, String ruleSetName,
                                   List<FileResult> results, long startMs, boolean isAuto) {
        long costSec = (System.currentTimeMillis() - startMs) / 1000;
        StringBuilder sb = new StringBuilder();

        sb.append("# Code Analysis Report\n\n");
        sb.append("| Property | Value |\n|---|---|\n");
        sb.append("| Project | ").append(nullSafe(projectName)).append(" |\n");
        if (repoUrl != null) sb.append("| Repo | ").append(repoUrl).append(" |\n");
        sb.append("| Standard | ").append(nullSafe(ruleSetName)).append(" |\n");
        sb.append("| Files Analyzed | ").append(results.size()).append(" |\n");

        int totalIssues = 0;
        for (FileResult r : results) totalIssues += r.issues;
        sb.append("| Issues Found | ").append(totalIssues).append(" |\n");
        sb.append("| Time | ").append(costSec).append(" seconds |\n\n");

        // Count by severity
        int high = 0, med = 0, low = 0;
        for (FileResult r : results) {
            if (r.analysis == null) continue;
            high += countEmoji(r.analysis, "\uD83D\uDD34");
            med  += countEmoji(r.analysis, "\uD83D\uDFE1");
            low  += countEmoji(r.analysis, "\uD83D\uDFE2");
        }
        sb.append("## Summary\n\n");
        sb.append("- \uD83D\uDD34 High severity: **").append(high).append("**\n");
        sb.append("- \uD83D\uDFE1 Medium severity: **").append(med).append("**\n");
        sb.append("- \uD83D\uDFE2 Low severity: **").append(low).append("**\n");
        // Auto mode: rule distribution
        if (isAuto) {
            Map<String, Integer> ruleFiles = new LinkedHashMap<>();
            Map<String, Integer> ruleIssues = new LinkedHashMap<>();
            for (FileResult r : results) {
                String rn = r.ruleName != null ? r.ruleName : "Unknown";
                ruleFiles.merge(rn, 1, Integer::sum);
                ruleIssues.merge(rn, r.issues, Integer::sum);
            }
            sb.append("\n### Rule Distribution\n\n");
            sb.append("| Rule | Files | Issues |\n|---|---|---|\n");
            for (Map.Entry<String, Integer> e : ruleFiles.entrySet()) {
                sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue())
                  .append(" | ").append(ruleIssues.get(e.getKey())).append(" |\n");
            }
        }
        sb.append("\n");

        // Sort by issue count descending
        results.sort((a, b) -> b.issues - a.issues);

        if (high > 0) {
            sb.append("## High Severity Files (Priority Fix)\n\n");
            for (FileResult r : results) {
                if (r.analysis != null && countEmoji(r.analysis, "\uD83D\uDD34") > 0) {
                    sb.append("### `").append(r.filePath).append("`");
                    if (isAuto && r.ruleName != null) sb.append(" [").append(r.ruleName).append("]");
                    sb.append("\n").append(r.analysis).append("\n\n");
                }
            }
        }

        sb.append("## Detailed Results\n\n");
        for (FileResult r : results) {
            if ("empty".equals(r.status)) {
                sb.append("### `").append(r.filePath).append("` - empty file, skipped\n\n");
            } else if ("ok".equals(r.status) || "chunked".equals(r.status)) {
                sb.append("### `").append(r.filePath).append("`");
                if (isAuto && r.ruleName != null) sb.append(" [").append(r.ruleName).append("]");
                if (r.issues > 0) sb.append(" - ").append(r.issues).append(" issue(s)");
                sb.append("\n\n").append(r.analysis).append("\n\n");
            } else if ("error".equals(r.status)) {
                sb.append("### `").append(r.filePath).append("` - analysis failed\n").append(r.analysis).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String nullSafe(String s) { return s != null ? s : "-"; }

    private int countEmoji(String text, String emoji) {
        if (text == null) return 0;
        int count = 0, idx = 0;
        while ((idx = text.indexOf(emoji, idx)) >= 0) { count++; idx += emoji.length(); }
        return count;
    }

    // Git utilities
    private int runGit(File dir, int timeoutSec, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return -1;
            }
            return p.exitValue();
        } catch (Exception e) { return -1; }
    }

    private String captureGit(File dir, int timeoutSec, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
            }
            p.waitFor(timeoutSec, TimeUnit.SECONDS);
            return sb.toString();
        } catch (Exception e) { return "[ERROR] " + e.getMessage(); }
    }

    private String getExtension(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        return filename.substring(dot + 1);
    }

    private String getTmpDir() {
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null || tmp.isEmpty()) tmp = System.getenv("TEMP");
        if (tmp == null || tmp.isEmpty()) tmp = "C:\\Temp";
        return tmp;
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) deleteDir(f);
        }
        dir.delete();
    }

    // ========== Historical Incident Check ==========
    private final ProductionIncidentDAO incidentDAO = new ProductionIncidentDAO();

    /**
     * Append historical incident risk check to report
     */
    private String appendIncidentCheck(String report, List<FileResult> results) {
        // Extract file paths and keywords
        List<String> filePaths = results.stream()
            .map(r -> r.filePath)
            .collect(Collectors.toList());
        
        List<String> keywords = extractKeywordsFromResults(results);
        
        // Build code content from analysis results for snippet matching
        StringBuilder codeContentBuilder = new StringBuilder();
        for (FileResult r : results) {
            if (r.analysis != null && !r.analysis.isEmpty()) {
                codeContentBuilder.append(r.analysis).append("\n");
            }
        }
        String codeContent = codeContentBuilder.length() > 2000 ? codeContentBuilder.substring(0, 2000) : codeContentBuilder.toString();
        
        // Call check service with code content
        IncidentCheckService checkService = new IncidentCheckService();
        IncidentCheckService.CheckResult checkResult = checkService.checkCode(
            codeContent, filePaths, keywords);
        
        if (!checkResult.hasRisk) {
            this.threadLocalIncidentCheckResult.set(null);
            return report;
        }
        
        // Build risk section (separate string for ThreadLocal)
        StringBuilder riskSb = new StringBuilder();
        riskSb.append("\n\n## [!] Historical Incident Risk Check\n\n");
        riskSb.append("WARNING: Found ").append(checkResult.matches.size()).append(" related historical issues\n\n");
        
        riskSb.append("| Incident No | Severity | Match Reason | Recommendation |\n");
        riskSb.append("|------------|----------|-------------|---------------|\n");
        
        for (var match : checkResult.matches) {
            riskSb.append("| ").append(match.incident.getIncidentNo()).append(" | ");
            riskSb.append(match.incident.getSeverity()).append(" | ");
            riskSb.append(match.matchReasons).append(" | ");
            riskSb.append(truncate(match.incident.getSolution(), 50)).append(" |\n");
        }
        
        riskSb.append("\n**Risk Level**: ").append(checkResult.riskLevel).append("\n");
        riskSb.append("\n**Recommendations**:\n");
        for (String rec : checkResult.recommendations) {
            riskSb.append("- ").append(rec).append("\n");
        }
        
        String riskSection = riskSb.toString();
        // Store to ThreadLocal for separate retrieval
        this.threadLocalIncidentCheckResult.set(riskSection);
        
        // Append to report and return
        return report + riskSection;
    }

    private List<String> extractKeywordsFromResults(List<FileResult> results) {
        List<String> keywords = new ArrayList<>();
        Set<String> added = new HashSet<>();
        
        for (FileResult r : results) {
            if (r.analysis == null) continue;
            // Extract keywords from analysis results
            if (r.analysis.contains("SQL") && !added.contains("SQL")) {
                keywords.add("SQL"); added.add("SQL");
            }
            if ((r.analysis.contains("injection")) && !added.contains("injection")) {
                keywords.add("injection"); added.add("injection");
            }
            if ((r.analysis.contains("XSS")) && !added.contains("XSS")) {
                keywords.add("XSS"); added.add("XSS");
            }
            if ((r.analysis.contains("permission") || r.analysis.contains("authorization")) && !added.contains("permission")) {
                keywords.add("permission"); added.add("permission");
            }
            if ((r.analysis.contains("encrypt") || r.analysis.contains("crypto")) && !added.contains("encrypt")) {
                keywords.add("encrypt"); added.add("encrypt");
            }
            if ((r.analysis.contains("null") || r.analysis.contains("NullPointer")) && !added.contains("null")) {
                keywords.add("null"); added.add("null");
            }
            if ((r.analysis.contains("memory") || r.analysis.contains("leak")) && !added.contains("memory")) {
                keywords.add("memory"); added.add("memory");
            }
            if ((r.analysis.contains("concurrent") || r.analysis.contains("race")) && !added.contains("concurrent")) {
                keywords.add("concurrent"); added.add("concurrent");
            }
        }
        
        return keywords;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    // Inner classes
    public static class ScannedFile {
        public final String path, content;
        public ScannedFile(String path, String content) { this.path = path; this.content = content; }
    }

    public static class FileResult {
        public final String filePath;
        public String status;
        public String analysis;
        public int issues;
        public String ruleName;  // which rule set was used (auto mode)
        public FileResult(String path) { this.filePath = path; }
    }

    public static class FileInfo {
        public final String path, content;
        public FileInfo(String path, String content) { this.path = path; this.content = content; }
    }

    public static class ProgressInfo {
        public final int analyzed, total;
        public final String currentFile, error;
        public ProgressInfo(int analyzed, int total, String currentFile, String error) {
            this.analyzed = analyzed; this.total = total;
            this.currentFile = currentFile; this.error = error;
        }
    }
public String getLastIncidentCheckResult() {
        String result = threadLocalIncidentCheckResult.get();
        threadLocalIncidentCheckResult.remove();
        return result;
    }
}
