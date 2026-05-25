package com.qiuniu.model;

/**
 * MCP提示词实体
 */
public class McpPrompt {

    private String promptId; // 提示词编号 varchar(10)
    private String content; // 提示词 text

    public String getPromptId() { return promptId; }
    public void setPromptId(String promptId) { this.promptId = promptId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
