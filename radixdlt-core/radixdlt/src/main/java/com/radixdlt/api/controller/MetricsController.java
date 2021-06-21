/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.controller;

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.api.Controller;
import com.radixdlt.api.service.MetricsService;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;

import static com.radixdlt.api.RestUtils.sanitizeBaseUrl;

public class MetricsController implements Controller {
	public static final String CONTENT_TYPE_TEXT_PLAIN_004 = "text/plain;version=0.0.4;charset=utf-8";

	private final MetricsService metricsService;

	public MetricsController(MetricsService metricsService) {
		this.metricsService = metricsService;
	}

	@Override
	public void configureRoutes(String root, RoutingHandler handler) {
		handler.get(sanitizeBaseUrl(root), this::handleMetricsRequest);
	}

	@VisibleForTesting
	void handleMetricsRequest(HttpServerExchange exchange) {
		exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, CONTENT_TYPE_TEXT_PLAIN_004);
		exchange.getResponseSender().send(metricsService.getMetrics());
	}
}
