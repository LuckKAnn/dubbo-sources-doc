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
package org.apache.dubbo.rpc.cluster.support;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.LoadBalance;

import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_KEY;

/**
 *
 */
public class RegistryAwareClusterInvoker<T> extends AbstractClusterInvoker<T> {

    private static final Logger logger = LoggerFactory.getLogger(RegistryAwareClusterInvoker.class);

    public RegistryAwareClusterInvoker(Directory<T> directory) {
        super(directory);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    //这里的invokers是多个注册中心的URL
    //每个注册中心对应的invoker，调用invoker方法的时候会去注册中心查询
    public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        // First, pick the invoker (XXXClusterInvoker) that comes from the local registry, distinguish by a 'default' key.
        // 如果在引入服务时，存在多个invoker，并且某个invoker是default的，则在调用时会使用该invoker
        for (Invoker<T> invoker : invokers) {
            //如果设置了Default的直接用了并返回
            if (invoker.isAvailable() && invoker.getUrl().getParameter(REGISTRY_KEY + "." + DEFAULT_KEY, false)) {
                return invoker.invoke(invocation);
            }
        }

        // If none of the invokers has a local signal, pick the first one available.
        // 如果没有default，则取第一个
        for (Invoker<T> invoker : invokers) {
            // 如果对应的注册中心中没有当前调用的服务信息，则不可用
            if (invoker.isAvailable()) {
                return invoker.invoke(invocation);
            }
        }
        throw new RpcException("No provider available in " + invokers);
    }
}
