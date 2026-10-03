package com.kun.mianshikun.model.entity;

import lombok.Data;

@Data
public class Hotkey<T> {
    private Long LogicExpire;
    private T Content;
}
