package com.kun.mianshikun.blackfilter;

import com.kun.mianshikun.manager.BlackListManager;
import com.kun.mianshikun.utils.NetUtils;
import java.io.IOException;
import javax.annotation.Resource;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@WebFilter(filterName = "blackFilter", urlPatterns = "/*")
public class BlackFilter implements Filter {

    @Resource
    private BlackListManager blackListManager;

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain)
            throws IOException, ServletException {
        String address = NetUtils.getIpAddress((HttpServletRequest) servletRequest);
        if (blackListManager.isBlack(address)) {
            log.warn("黑名单用户被拦截：{}", address);
            servletResponse.setContentType("text/html;charset=utf-8");
            servletResponse.getWriter().write("黑名单用户");
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
