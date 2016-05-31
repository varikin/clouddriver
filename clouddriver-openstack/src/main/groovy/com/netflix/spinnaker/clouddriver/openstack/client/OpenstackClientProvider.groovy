/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.OpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.api.Builders
import org.openstack4j.api.OSClient
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.RebootType

/**
 * Provides access to the Openstack API.
 *
 * TODO tokens will need to be regenerated if they are expired.
 */
abstract class OpenstackClientProvider {

  /**
   * Delete an instance.
   * @param instanceId
   * @return
   */
  void deleteInstance(String instanceId) {
    handleRequest(AtomicOperations.TERMINATE_INSTANCES) {
      client.compute().servers().delete(instanceId)
    }
  }

  /**
   * Reboot an instance ... Default to SOFT reboot if not passed.
   * @param instanceId
   * @return
   */
  void rebootInstance(String instanceId, RebootType rebootType = RebootType.SOFT) {
    handleRequest(AtomicOperations.REBOOT_INSTANCES) {
      client.compute().servers().reboot(instanceId, rebootType)
    }
  }

  //TODO test
  //TODO wrap calls to client in handleRequest closure. Some calls dont return
  //and ActionResponse so we need to be able to handle this.
  /**
   * Create or update a security group, applying a list of rules.
   * @param securityGroupName
   * @param description
   * @param rules
   */
  void upsertSecurityGroup(String securityGroupName, String description, List<OpenstackSecurityGroupDescription.Rule> rules) {

    //try getting existing security group
    def existing = client.compute().securityGroups().get(securityGroupName)
    if (existing == null) {
      existing = client.compute().securityGroups().create(securityGroupName, description)
    }

    //remove existing rules
    existing.rules.each { rule ->
      client.compute().securityGroups().deleteRule(rule.id)
    }

    //add new rules
    rules.each { rule ->
    client.compute().securityGroups().createRule(Builders.secGroupRule()
      .parentGroupId(existing.id)
      .protocol(IPProtocol.valueOf(rule.ruleType))
      .cidr(rule.cidr)
      .range(rule.fromPort, rule.toPort).build())
    }

  }

  /**
   * Handler for an openstack4j request.
   * @param closure
   * @return
   */
  ActionResponse handleRequest(String operation, Closure closure) {
    ActionResponse result
    try {
      result = closure()
    } catch (Exception e) {
      throw new OpenstackOperationException(operation, e)
    }
    if (!result.isSuccess()) {
      throw new OpenstackOperationException(result, operation)
    }
    result
  }

  /**
   * Thread-safe way to get client.
   * @return
   */
  abstract OSClient getClient()

  /**
   * Get a new token id.
   * @return
   */
  abstract String getTokenId()
}
