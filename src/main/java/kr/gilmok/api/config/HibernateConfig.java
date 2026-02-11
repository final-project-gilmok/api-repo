package kr.gilmok.api.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.hibernate5.SpringBeanContainer;

@Configuration
public class HibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            ConfigurableListableBeanFactory beanFactory) {
        return props -> props.put(
                AvailableSettings.BEAN_CONTAINER,
                new SpringBeanContainer(beanFactory)
        );
    }
}
