/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.graphql;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.stream.Collectors;

import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

@Configuration
@ConditionalOnClass(GraphQL.class)
@ConditionalOnMissingBean(GraphQL.class)
@EnableConfigurationProperties(GraphQLProperties.class)
public class GraphQLAutoConfiguration {

	@Configuration
	@ConditionalOnMissingBean(GraphQL.Builder.class)
	static class SdlConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public RuntimeWiring runtimeWiring(ObjectProvider<RuntimeWiringCustomizer> customizers) {
			RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
			customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
			return builder.build();
		}

		@Bean
		public GraphQL.Builder graphQLBuilder(GraphQLProperties properties, RuntimeWiring runtimeWiring,
				ObjectProvider<Instrumentation> instrumentationsProvider) {
			try {
				File schemaFile = ResourceUtils.getFile(properties.getSchemaLocation());
				GraphQLSchema schema = buildSchema(schemaFile, runtimeWiring);
				GraphQL.Builder builder = GraphQL.newGraphQL(schema);
				List<Instrumentation> instrumentations = instrumentationsProvider.orderedStream().collect(Collectors.toList());
				if (!instrumentations.isEmpty()) {
					builder = builder.instrumentation(new ChainedInstrumentation(instrumentations));
				}
				return builder;
			}
			catch (FileNotFoundException ex) {
				throw new MissingGraphQLSchemaException(properties.getSchemaLocation());
			}
		}

		private GraphQLSchema buildSchema(File schemaFile, RuntimeWiring runtimeWiring) {
			TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaFile);
			SchemaGenerator schemaGenerator = new SchemaGenerator();
			return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
		}

	}

}
