/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl;

import java.util.List;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.identity.Authorization;
import org.camunda.bpm.engine.identity.AuthorizationQuery;
import org.camunda.bpm.engine.identity.Permission;
import org.camunda.bpm.engine.identity.Resource;
import org.camunda.bpm.engine.impl.cmd.AuthorizationCheckCmd;
import org.camunda.bpm.engine.impl.cmd.DeleteAuthorizationCmd;
import org.camunda.bpm.engine.impl.cmd.SaveAuthorizationCmd;
import org.camunda.bpm.engine.impl.persistence.entity.AuthorizationEntity;

/**
 * @author Daniel Meyer
 *
 */
public class AuthorizationServiceImpl extends ServiceImpl implements AuthorizationService {
  
  public AuthorizationQuery createAuthorizationQuery() {
    return new AuthorizationQueryImpl(commandExecutor);
  }
  
  public Authorization createNewAuthorization() {
    return new AuthorizationEntity();
  }
  
  public Authorization saveAuthorization(Authorization authorization) {
    return commandExecutor.execute(new SaveAuthorizationCmd(authorization));
  }
  
  public void deleteAuthorization(String authorizationId) {
    commandExecutor.execute(new DeleteAuthorizationCmd(authorizationId));    
  }

  public boolean isUserAuthorized(String userId, List<String> groupIds, Permission permission, Resource resource) {
    return commandExecutor.execute(new AuthorizationCheckCmd(userId, groupIds, permission, resource, null));
  }

  public boolean isUserAuthorized(String userId, List<String> groupIds, Permission permission, Resource resource, String resourceId) {
    return commandExecutor.execute(new AuthorizationCheckCmd(userId, groupIds, permission, resource, resourceId));
  }
  
}