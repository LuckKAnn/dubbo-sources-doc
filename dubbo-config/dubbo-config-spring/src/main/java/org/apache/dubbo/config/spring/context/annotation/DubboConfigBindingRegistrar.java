/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config.spring.context.annotation;

import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.spring.beans.factory.annotation.DubboConfigBindingBeanPostProcessor;
import org.apache.dubbo.config.spring.context.config.NamePropertyDefaultValueDubboConfigBeanCustomizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.dubbo.config.spring.context.config.NamePropertyDefaultValueDubboConfigBeanCustomizer.BEAN_NAME;
import static org.apache.dubbo.config.spring.util.BeanRegistrar.registerInfrastructureBean;
import static org.apache.dubbo.config.spring.util.PropertySourcesUtils.getSubProperties;
import static org.apache.dubbo.config.spring.util.PropertySourcesUtils.normalizePrefix;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;
import static org.springframework.beans.factory.support.BeanDefinitionReaderUtils.registerWithGeneratedName;

/**
 * {@link AbstractConfig Dubbo Config} binding Bean registrar
 *
 * @see EnableDubboConfigBinding
 * @see DubboConfigBindingBeanPostProcessor
 * @since 2.5.8
 */
public class DubboConfigBindingRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private final Log log = LogFactory.getLog(getClass());

    //spring?????????Environment
    private ConfigurableEnvironment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        System.out.println("??????DubboConfigBindingRegistrar");

        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableDubboConfigBinding.class.getName()));

        registerBeanDefinitions(attributes, registry);

    }

    protected void registerBeanDefinitions(AnnotationAttributes attributes, BeanDefinitionRegistry registry) {

        // prefix = "dubbo.application"
        String prefix = environment.resolvePlaceholders(attributes.getString("prefix"));

        // type = ApplicationConfig.class
        Class<? extends AbstractConfig> configClass = attributes.getClass("type");

        boolean multiple = attributes.getBoolean("multiple");



        //
        registerDubboConfigBeans(prefix, configClass, multiple, registry);

    }

    private void registerDubboConfigBeans(String prefix,
                                          Class<? extends AbstractConfig> configClass,
                                          boolean multiple,
                                          BeanDefinitionRegistry registry) {

        // ???properties?????????????????????????????????????????????????????????dubbo.application?????????
        // ??????????????????
        // dubbo.application.name=dubbo-demo-provider-application
        // dubbo.application.logger=log4j
        Map<String, Object> properties = getSubProperties(environment.getPropertySources(), prefix);

        // ???????????????????????????????????????????????????BeanDefinition
        if (CollectionUtils.isEmpty(properties)) {
            if (log.isDebugEnabled()) {
                log.debug("There is no property for binding to dubbo config class [" + configClass.getName()
                        + "] within prefix [" + prefix + "]");
            }
            return;
        }

        // ?????????????????????beanNames???????????????????????????
        // ??????????????????dubbo.application??????????????????ApplicationConfig?????????Bean
        // ????????????????????????dubbo.protocols????????????
//        dubbo.protocols.p1.name=dubbo
//        dubbo.protocols.p1.port=20880
//        dubbo.protocols.p1.host=0.0.0.0

//        dubbo.protocols.p2.name=http
//        dubbo.protocols.p2.port=8082
//        dubbo.protocols.p2.host=0.0.0.0
        // ???????????????????????????ProtocolConfig?????????Bean????????????????????????beanName:p1???p2

        // ????????????multiple???true???false????????????????????????????????????multiple???????????????????????????????????????beanName
        // ??????multiple???false????????????????????????id????????????????????????????????????????????????beanName.
        Set<String> beanNames = multiple ? resolveMultipleBeanNames(properties) :
                Collections.singleton(resolveSingleBeanName(properties, configClass, registry));

        for (String beanName : beanNames) {

            // ?????????beanName,??????????????????BeanDefinition???????????????????????????
            registerDubboConfigBean(beanName, configClass, registry);

            // ?????????bean????????????DubboConfigBindingBeanPostProcessor???Bean???????????????
            registerDubboConfigBindingBeanPostProcessor(prefix, beanName, multiple, registry);

        }

        // ????????????NamePropertyDefaultValueDubboConfigBeanCustomizer???bean
        // ???????????????XxConfig????????????beanName?????????name????????????
        registerDubboConfigBeanCustomizers(registry);

    }

    private void registerDubboConfigBean(String beanName, Class<? extends AbstractConfig> configClass,
                                         BeanDefinitionRegistry registry) {

        BeanDefinitionBuilder builder = rootBeanDefinition(configClass);

        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();

        registry.registerBeanDefinition(beanName, beanDefinition);   // ApplicatinoConfig??????

        if (log.isInfoEnabled()) {
            log.info("The dubbo config bean definition [name : " + beanName + ", class : " + configClass.getName() +
                    "] has been registered.");
        }

    }

    private void registerDubboConfigBindingBeanPostProcessor(String prefix, String beanName, boolean multiple,
                                                             BeanDefinitionRegistry registry) {

        // ????????????DubboConfigBindingBeanPostProcessor???Bean
        // ??????XxConfig???Bean????????????DubboConfigBindingBeanPostProcessor???Bean
        // ???????????????ApplicationConfig????????????DubboConfigBindingBeanPostProcessor???
        // ??????ProtocolConfig??????????????????DubboConfigBindingBeanPostProcessor
        // ?????????DubboConfigBindingBeanPostProcessor?????????????????????????????????????????????????????????????????????

        Class<?> processorClass = DubboConfigBindingBeanPostProcessor.class;

        BeanDefinitionBuilder builder = rootBeanDefinition(processorClass);

        // ????????????????????????dubbo.registries.r2
        String actualPrefix = multiple ? normalizePrefix(prefix) + beanName : prefix;

        // ???????????????????????????????????????????????????DubboConfigBindingBeanPostProcessor??????????????????????????????
        builder.addConstructorArgValue(actualPrefix).addConstructorArgValue(beanName);

        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();

        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

        registerWithGeneratedName(beanDefinition, registry);

        if (log.isInfoEnabled()) {
            log.info("The BeanPostProcessor bean definition [" + processorClass.getName()
                    + "] for dubbo config bean [name : " + beanName + "] has been registered.");
        }

    }

    private void registerDubboConfigBeanCustomizers(BeanDefinitionRegistry registry) {
        registerInfrastructureBean(registry, BEAN_NAME, NamePropertyDefaultValueDubboConfigBeanCustomizer.class);
    }

    @Override
    public void setEnvironment(Environment environment) {

        Assert.isInstanceOf(ConfigurableEnvironment.class, environment);

        this.environment = (ConfigurableEnvironment) environment;

    }

    private Set<String> resolveMultipleBeanNames(Map<String, Object> properties) {

        Set<String> beanNames = new LinkedHashSet<String>();

        // ??????dubbo.protocols.p1.name=dubbo???propertyName???p1.name

        for (String propertyName : properties.keySet()) {

            // propertyName???p1.name
            int index = propertyName.indexOf(".");

            if (index > 0) {

                // ??????beanName?????????p1
                String beanName = propertyName.substring(0, index);

                beanNames.add(beanName);
            }

        }

        return beanNames;

    }

    private String resolveSingleBeanName(Map<String, Object> properties, Class<? extends AbstractConfig> configClass,
                                         BeanDefinitionRegistry registry) {

        // ?????????dubbo.application.id=appl?????????appl??????beanName
        String beanName = (String) properties.get("id");
        // ??????beanName???null???????????????if????????????spring??????????????????beanName,??????org.apache.dubbo.config.ApplicationConfig#0
        if (!StringUtils.hasText(beanName)) {
            BeanDefinitionBuilder builder = rootBeanDefinition(configClass);
            beanName = BeanDefinitionReaderUtils.generateBeanName(builder.getRawBeanDefinition(), registry);
        }

        return beanName;

    }

}