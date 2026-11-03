package br.ufla.citogenetica.configuration;

import br.ufla.citogenetica.filter.AuthRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AuthRateLimitWebConfiguration implements WebMvcConfigurer {

    private final AuthRateLimitInterceptor interceptor;

    public AuthRateLimitWebConfiguration(AuthRateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
