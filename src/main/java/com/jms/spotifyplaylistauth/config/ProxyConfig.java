package com.jms.spotifyplaylistauth.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;

/**
 * Configuration to handle HTTPS properly when running behind a proxy (like on Koyeb)
 */
@Configuration
public class ProxyConfig {

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new ForwardedHeaderFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    /**
     * Filter to handle X-Forwarded-* headers
     */
    public static class ForwardedHeaderFilter implements Filter {
        
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String forwardedProto = httpRequest.getHeader("X-Forwarded-Proto");
            
            if ("https".equals(forwardedProto)) {
                // Wrap the request to override the scheme
                HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
                    @Override
                    public String getScheme() {
                        return "https";
                    }
                    
                    @Override
                    public boolean isSecure() {
                        return true;
                    }
                    
                    @Override
                    public StringBuffer getRequestURL() {
                        StringBuffer url = new StringBuffer();
                        url.append("https://");
                        url.append(getServerName());
                        if (getServerPort() != 80 && getServerPort() != 443) {
                            url.append(':');
                            url.append(getServerPort());
                        }
                        url.append(getRequestURI());
                        return url;
                    }
                };
                chain.doFilter(wrappedRequest, response);
            } else {
                chain.doFilter(request, response);
            }
        }
    }
}
