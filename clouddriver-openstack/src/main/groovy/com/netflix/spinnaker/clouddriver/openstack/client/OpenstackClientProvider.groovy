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
import org.apache.commons.lang.StringUtils
import org.openstack4j.api.Builders
import org.openstack4j.api.OSClient
import org.openstack4j.api.compute.ComputeSecurityGroupService
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.RebootType
import org.openstack4j.model.compute.SecGroupExtension

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
  /**
   * Create or update a security group, applying a list of rules. If the securityGroupId is provided, updates an existing
   * security group, else creates a new security group.
   *
   * Note: 2 default egress rules are created when creating a new security group
   * automatically with remote IP prefixes 0.0.0.0/0 and ::/0.
   *
   * @param securityGroupId id of an existing security group to update
   * @param securityGroupName name security group
   * @param description description of the security group
   * @param rules list of rules for the security group
   */
  void upsertSecurityGroup(String securityGroupId, String securityGroupName, String description, List<OpenstackSecurityGroupDescription.Rule> rules) {

     handleRequest(AtomicOperations.UPSERT_SECURITY_GROUP) {

       // The call to getClient reauthentictes via a token, so grab once for this method
       def securityGroupsApi = client.compute().securityGroups()

      // Try getting existing security group, update if needed
      SecGroupExtension existing
      if (StringUtils.isNotEmpty(securityGroupId)) {
        existing = securityGroupsApi.get(securityGroupId)
      }
      if (existing == null) {
        existing = securityGroupsApi.create(securityGroupName, description)
      } else {
        securityGroupsApi.update(existing.id, securityGroupName, description)
      }

      //remove existing rules
      existing.rules.each { rule ->
        securityGroupsApi.deleteRule(rule.id)
      }

      //add new rules
      rules.each { rule ->
        securityGroupsApi.createRule(Builders.secGroupRule()
          .parentGroupId(existing.id)
          .protocol(IPProtocol.valueOf(rule.ruleType))
          .cidr(rule.cidr)
          .range(rule.fromPort, rule.toPort).build())
      }
    }
  }

  /**
   * Handler for an Openstack4J request with error common handling.
   * @param operation to add context to error messages
   * @param closure makes the needed Openstack4J request
   * @return returns the result from the closure
   */
  def handleRequest(String operation, Closure closure) {
    def result
    try {
      result = closure()
    } catch (Exception e) {
      throw new OpenstackOperationException(operation, e)
    }
    if (result instanceof ActionResponse && !result.isSuccess()) {
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
