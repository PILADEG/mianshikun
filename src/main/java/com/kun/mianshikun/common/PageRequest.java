package com.kun.mianshikun.common;

import com.kun.mianshikun.constant.CommonConstant;
import lombok.Data;

@Data
public class PageRequest {

    private int current = 1;

    private int pageSize = 10;

    private String sortField;

    private String sortOrder = CommonConstant.SORT_ORDER_ASC;
}
