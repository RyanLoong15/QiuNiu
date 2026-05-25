package com.qiuniu.model;

/**
 * 用户角色枚举
 */
public enum Role {
    /**
     * 超级管理员 - 拥有系统所有权限
     */
    ADMIN("ADMIN", "超级管理员"),
    
    /**
     * 查看人员 - 只读权限，不能修改任何数据
     */
    VIEWER("VIEWER", "查看人员"),
    
    /**
     * 团队管理员 - 只能管理自己团队的数据
     */
    TEAM_ADMIN("TEAM_ADMIN", "团队管理员");
    
    private final String code;
    private final String displayName;
    
    Role(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 根据代码获取角色
     */
    public static Role fromCode(String code) {
        if (code == null) {
            return VIEWER; // 默认为查看人员
        }
        for (Role role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        return VIEWER;
    }
    
    /**
     * 判断是否为超级管理员
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }
    
    /**
     * 判断是否可以修改数据（写权限）
     */
    public boolean canWrite() {
        return this == ADMIN || this == TEAM_ADMIN;
    }
    
    /**
     * 判断是否可以查看所有面板
     */
    public boolean canViewAll() {
        return true; // 所有角色都可以查看
    }
}
