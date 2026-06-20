package org.example.auth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank(message = "请输入账号")
    @Size(min = 3, max = 50, message = "账号长度需为 3-50 位")
    @Pattern(regexp = "^[a-zA-Z0-9_@.-]+$", message = "账号只能包含字母、数字及 _ @ . -")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 100, message = "密码至少 6 位")
    private String password;

    @NotBlank(message = "请确认密码")
    @Size(min = 6, max = 100, message = "确认密码至少 6 位")
    private String confirmPassword;

    @NotBlank(message = "请输入昵称")
    @Size(min = 1, max = 50, message = "昵称长度不能超过 50 位")
    private String nickname;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

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

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}

