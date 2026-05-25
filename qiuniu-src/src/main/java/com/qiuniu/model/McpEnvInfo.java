package com.qiuniu.model;

/**
 * MCP测试环境信息实体
 */
public class McpEnvInfo {

    private String envId;        // MCP环境信息编号 varchar(10)
    private String systemName;   // 系统名 varchar(20)
    private String environment;  // 环境 varchar(20)
    private String clusterCode;  // 集群码 varchar(30)
    private String machineName;  // 机器名 varchar(30)
    private String ipAddress;    // IP地址 varchar(30)
    private String username;     // 用户 varchar(50)
    private String password;     // 密码 varchar(50)
    private String commandId;    // 命令编号 varchar(10)
    private String promptId;     // 提示词编号 varchar(10)

    public String getEnvId() { return envId; }
    public void setEnvId(String envId) { this.envId = envId; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getClusterCode() { return clusterCode; }
    public void setClusterCode(String clusterCode) { this.clusterCode = clusterCode; }
    public String getMachineName() { return machineName; }
    public void setMachineName(String machineName) { this.machineName = machineName; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }
    public String getPromptId() { return promptId; }
    public void setPromptId(String promptId) { this.promptId = promptId; }
}
