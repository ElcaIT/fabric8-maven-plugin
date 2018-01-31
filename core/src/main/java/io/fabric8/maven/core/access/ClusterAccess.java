/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.fabric8.maven.core.access;

import java.net.UnknownHostException;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.*;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftAPIGroups;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import io.fabric8.utils.Strings;

import static io.fabric8.kubernetes.api.KubernetesHelper.DEFAULT_NAMESPACE;

/**
 * @author roland
 * @since 17/07/16
 */
public class ClusterAccess {

	public static final Long OPENSHIFT_BUILD_TIMEOUT = 30 * 60 * 1000L;
    private String namespace;

    public ClusterAccess(String namespace) {
        this.namespace = namespace;

        if (Strings.isNullOrBlank(this.namespace)) {
            this.namespace = KubernetesHelper.defaultNamespace();
        }
        if (Strings.isNullOrBlank(this.namespace)) {
            this.namespace = DEFAULT_NAMESPACE;
        }
    }

    public KubernetesClient createDefaultClient(Logger log) {
        if (isOpenShift(log)) {
            return createOpenShiftClient();
        }

        return createKubernetesClient();
    }

    public KubernetesClient createKubernetesClient() {
        return new DefaultKubernetesClient(createDefaultConfig());
    }

    public OpenShiftClient createOpenShiftClient() {
        return new DefaultOpenShiftClient(createDefaultOpenShiftConfig());
    }

    // ============================================================================

    private Config createDefaultConfig() {
        return new ConfigBuilder().withNamespace(getNamespace()).build();
    }
    
	private OpenShiftConfig createDefaultOpenShiftConfig() {
		OpenShiftConfig openShiftConfig = new OpenShiftConfigBuilder().withBuildTimeout(OPENSHIFT_BUILD_TIMEOUT)
				.withNamespace(getNamespace()).build();
		return openShiftConfig;
	}
    	        
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns true if this cluster is a traditional OpenShift cluster with the <code>/oapi</code> REST API
     * or supports the new <code>/apis/image.openshift.io</code> API Group
     */
    public boolean isOpenShiftImageStream(Logger log) {
        if (isOpenShift(log)) {
            OpenShiftClient openShiftClient = createOpenShiftClient();
            return openShiftClient.supportsOpenShiftAPIGroup(OpenShiftAPIGroups.IMAGE);
        }
        return false;
    }

    public boolean isOpenShift(Logger log) {
        try {
            return KubernetesHelper.isOpenShift(createKubernetesClient());
        } catch (KubernetesClientException exp) {
            Throwable cause = exp.getCause();
            String prefix = cause instanceof UnknownHostException ? "Unknown host " : "";
            log.warn("Cannot access cluster for detecting mode: %s%s",
                     prefix,
                     cause != null ? cause.getMessage() : exp.getMessage());
            return false;
        }
    }

    public PlatformMode resolvePlatformMode(PlatformMode mode, Logger log) {
        PlatformMode resolvedMode;
        if (mode == null) {
            mode = PlatformMode.DEFAULT;
        }
        if (mode.isAuto()) {
            resolvedMode = isOpenShiftImageStream(log) ? PlatformMode.openshift : PlatformMode.kubernetes;
        } else {
            resolvedMode = mode;
        }
        return resolvedMode;
    }
}

