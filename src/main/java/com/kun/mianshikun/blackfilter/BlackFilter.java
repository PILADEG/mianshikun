package com.kun.mianshikun.blackfilter;

import com.kun.mianshikun.utils.NetUtils;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@WebFilter(filterName = "blackFilter", urlPatterns = "/*")
public class BlackFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain)
            throws IOException, ServletException {
        String address = NetUtils.getIpAddress((HttpServletRequest) servletRequest);
        log.info("黑名单过滤器开始过滤：{}", address);
        if (BlackListUtils.isBlack(address)) {
            log.info("黑名单用户：{}", address);
            servletResponse.setContentType("text/html;charset=utf-8");
            servletResponse.getWriter().write("黑名单用户");
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
