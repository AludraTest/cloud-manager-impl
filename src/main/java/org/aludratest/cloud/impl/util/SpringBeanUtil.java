/*
 * Copyright (C) 2010-2015 AludraTest.org and the contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aludratest.cloud.impl.util;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

public final class SpringBeanUtil {

	private SpringBeanUtil() {
	}

	public static <T> Map<String, T> getBeansOfTypeByQualifier(Class<T> beanType,
			ApplicationContext applicationContext) {
		return applicationContext.getBeansOfType(beanType).values().stream()
				.collect(Collectors.toMap(v -> getBeanQualifier(v), v -> v));
	}

	private static String getBeanQualifier(Object bean) {
		Qualifier qualifier = AnnotationUtils.findAnnotation(bean.getClass(), Qualifier.class);
		if (qualifier == null || StringUtils.isEmpty(qualifier.value())) {
			return "default";
		}

		return qualifier.value();
	}

}
