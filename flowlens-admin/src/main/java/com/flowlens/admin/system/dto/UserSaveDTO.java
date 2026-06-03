package com.flowlens.admin.system.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class UserSaveDTO {

    private Long id;

    @NotBlank(message = "不能为空")
    private String username;

    private String password;

    @NotBlank(message = "不能为空")
    private String nickname;

    private String email;

    private String mobile;

    private Integer status;

    private List<Long> roleIds;
}
