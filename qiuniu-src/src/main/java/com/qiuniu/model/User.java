package com.qiuniu.model;

import java.util.Date;

/**
 * 用户模型类
 */
public class User {
    
    private Long id;
    private String username;
    private String password;
    private String email;
    private String nickname;
    private Role role; // 用户角色
    private Date createTime;
    private Date lastLoginTime;
    
    public User() {}
    
    public User(String username, String password, String email, String nickname) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
        this.role = Role.VIEWER; // 默认为查看人员
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public Date getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
    
    public Date getLastLoginTime() {
        return lastLoginTime;
    }
    
    public void setLastLoginTime(Date lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }
    
    /**
     * 判断是否为超级管理员
     */
    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }
    
    /**
     * 判断是否可以修改数据
     */
    public boolean canWrite() {
        return role != null && role.canWrite();
    }
}
