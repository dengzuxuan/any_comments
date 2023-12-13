package com.comments.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    private List<?> list; //问号是泛型的意思
    private Long minTime;
    private Integer offset;
}
