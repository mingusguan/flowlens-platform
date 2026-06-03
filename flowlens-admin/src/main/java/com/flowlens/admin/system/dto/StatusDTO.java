package com.flowlens.admin.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusDTO {

    @NotNull(message = "不能为空")
    private Integer status;
}
