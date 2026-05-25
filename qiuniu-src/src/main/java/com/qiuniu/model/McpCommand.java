package com.qiuniu.model;

/**
 * MCP执行命令实体
 */
public class McpCommand {

    private String commandId;   // 命令编号 varchar(10)
    private String command;     // 命令 varchar(1999)
    private String description; // 命令描述 text

    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
