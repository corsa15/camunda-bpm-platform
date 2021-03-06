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
package org.camunda.bpm.engine.impl.persistence.entity;

import static org.camunda.bpm.engine.authorization.Permissions.CREATE;
import static org.camunda.bpm.engine.authorization.Permissions.CREATE_INSTANCE;
import static org.camunda.bpm.engine.authorization.Permissions.DELETE;
import static org.camunda.bpm.engine.authorization.Permissions.DELETE_INSTANCE;
import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Permissions.READ_INSTANCE;
import static org.camunda.bpm.engine.authorization.Permissions.READ_TASK;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE_INSTANCE;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE_TASK;
import static org.camunda.bpm.engine.authorization.Resources.AUTHORIZATION;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_DEFINITION;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_INSTANCE;
import static org.camunda.bpm.engine.authorization.Resources.TASK;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.impl.AbstractQuery;
import org.camunda.bpm.engine.impl.ActivityStatisticsQueryImpl;
import org.camunda.bpm.engine.impl.AuthorizationQueryImpl;
import org.camunda.bpm.engine.impl.EventSubscriptionQueryImpl;
import org.camunda.bpm.engine.impl.IncidentQueryImpl;
import org.camunda.bpm.engine.impl.ProcessDefinitionQueryImpl;
import org.camunda.bpm.engine.impl.ProcessDefinitionStatisticsQueryImpl;
import org.camunda.bpm.engine.impl.TaskQueryImpl;
import org.camunda.bpm.engine.impl.VariableInstanceQueryImpl;
import org.camunda.bpm.engine.impl.db.AuthorizationCheck;
import org.camunda.bpm.engine.impl.db.DbEntity;
import org.camunda.bpm.engine.impl.db.PermissionCheck;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.impl.persistence.AbstractManager;

/**
 * @author Daniel Meyer
 *
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class AuthorizationManager extends AbstractManager {

  public static final String DEFAULT_AUTHORIZATION_CHECK = "defaultAuthorizationCheck";

  public Authorization createNewAuthorization(int type) {
    checkAuthorization(CREATE, AUTHORIZATION, null);
    return new AuthorizationEntity(type);
  }

  public void insert(DbEntity authorization) {
    checkAuthorization(CREATE, AUTHORIZATION, null);
    getDbEntityManager().insert(authorization);
  }

  public List<Authorization> selectAuthorizationByQueryCriteria(AuthorizationQueryImpl authorizationQuery) {
    configureQuery(authorizationQuery, AUTHORIZATION);
    return getDbEntityManager().selectList("selectAuthorizationByQueryCriteria", authorizationQuery);
  }

  public Long selectAuthorizationCountByQueryCriteria(AuthorizationQueryImpl authorizationQuery) {
    configureQuery(authorizationQuery, AUTHORIZATION);
    return (Long) getDbEntityManager().selectOne("selectAuthorizationCountByQueryCriteria", authorizationQuery);
  }

  public AuthorizationEntity findAuthorizationByUserIdAndResourceId(int type, String userId, Resource resource, String resourceId) {
    return findAuthorization(type, userId, null, resource, resourceId);
  }

  public AuthorizationEntity findAuthorizationByGroupIdAndResourceId(int type, String groupId, Resource resource, String resourceId) {
    return findAuthorization(type, null, groupId, resource, resourceId);
  }

  public AuthorizationEntity findAuthorization(int type, String userId, String groupId, Resource resource, String resourceId) {
    Map<String, Object> params = new HashMap<String, Object>();

    params.put("type", type);
    params.put("userId", userId);
    params.put("groupId", groupId);
    params.put("resourceId", resourceId);

    if (resource != null) {
      params.put("resourceType", resource.resourceType());
    }

    return (AuthorizationEntity) getDbEntityManager().selectOne("selectAuthorizationByParameters", params);
  }

  public void update(AuthorizationEntity authorization) {
    checkAuthorization(UPDATE, AUTHORIZATION, authorization.getId());
    getDbEntityManager().merge(authorization);
  }

  public void delete(DbEntity authorization) {
    checkAuthorization(DELETE, AUTHORIZATION, authorization.getId());
    deleteAuthorizationsByResourceId(AUTHORIZATION, authorization.getId());
    super.delete(authorization);
  }

  // authorization checks ///////////////////////////////////////////

  public void checkAuthorization(List<PermissionCheck> permissionChecks) {
    Authentication currentAuthentication = getCurrentAuthentication();

    if(isAuthorizationEnabled() && currentAuthentication != null) {

      String userId = currentAuthentication.getUserId();
      boolean isAuthorized = isAuthorized(userId, currentAuthentication.getGroupIds(), permissionChecks);
      if (!isAuthorized) {

        if (permissionChecks.size() == 1) {
          PermissionCheck permissionCheck = permissionChecks.get(0);
          String permissionName = permissionCheck.getPermission().getName();
          String resourceType = permissionCheck.getResource().resourceName();
          String resourceId = permissionCheck.getResourceId();
          throw new AuthorizationException(userId, permissionName, resourceType, resourceId);
        } else {

          String message = "The user with id '" + userId +
                           "' does not have one of the following permissions: ";

          for (int i = 0; i < permissionChecks.size(); i++) {

            if (i > 0) {
              message = message + " or ";
            }

            PermissionCheck permissionCheck = permissionChecks.get(i);
            String permissionName = permissionCheck.getPermission().getName();
            String resourceType = permissionCheck.getResource().resourceName();
            String resourceId = permissionCheck.getResourceId();

            message = message + "'"+permissionName+"' permission " +
                "on resource '" + (resourceId != null ? (resourceId+"' of type '") : "" ) + resourceType + "'";
          }
          throw new AuthorizationException(message);
        }
      }
    }
  }

  public void checkAuthorization(Permission permission, Resource resource) {
    checkAuthorization(permission, resource, null);
  }

  public void checkAuthorization(Permission permission, Resource resource, String resourceId) {

    final Authentication currentAuthentication = getCurrentAuthentication();

    if(isAuthorizationEnabled() && currentAuthentication != null) {

      boolean isAuthorized = isAuthorized(currentAuthentication.getUserId(), currentAuthentication.getGroupIds(), permission, resource, resourceId);
      if (!isAuthorized) {
        throw new AuthorizationException(currentAuthentication.getUserId(), permission.getName(), resource.resourceName(), resourceId);
      }
    }

  }

  public boolean isAuthorized(Permission permission, Resource resource, String resourceId) {

    final Authentication currentAuthentication = getCurrentAuthentication();

    if(isAuthorizationEnabled() && currentAuthentication != null) {
      return isAuthorized(currentAuthentication.getUserId(), currentAuthentication.getGroupIds(), permission, resource, resourceId);

    } else {
      return true;

    }
  }

  public boolean isAuthorized(String userId, List<String> groupIds, Permission permission, Resource resource, String resourceId) {
    PermissionCheck permCheck = new PermissionCheck();
    permCheck.setPermission(permission);
    permCheck.setResource(resource);
    permCheck.setResourceId(resourceId);

    return isAuthorized(userId, groupIds, Arrays.asList(permCheck));
  }

  public boolean isAuthorized(String userId, List<String> groupIds, List<PermissionCheck> permissionChecks) {
    AuthorizationCheck authCheck = new AuthorizationCheck();
    authCheck.setAuthUserId(userId);
    authCheck.setAuthGroupIds(groupIds);
    authCheck.setPermissionChecks(permissionChecks);
    return getDbEntityManager().selectBoolean("isUserAuthorizedForResource", authCheck);
  }

  // authorization checks on queries ////////////////////////////////

  public void configureQuery(AbstractQuery query) {
    final Authentication currentAuthentication = getCurrentAuthentication();

    query.getPermissionChecks().clear();

    if(isAuthorizationEnabled() && currentAuthentication != null) {

      query.setAuthorizationCheckEnabled(true);

      String currentUserId = currentAuthentication.getUserId();
      List<String> currentGroupIds = currentAuthentication.getGroupIds();

      query.setAuthUserId(currentUserId);
      query.setAuthGroupIds(currentGroupIds);
    }
    else {
      query.setAuthorizationCheckEnabled(false);
      query.setAuthUserId(null);
      query.setAuthGroupIds(null);
    }
  }

  public void configureQuery(AbstractQuery query, Resource resource) {
    configureQuery(query, resource, "RES.ID_");
  }

  public void configureQuery(AbstractQuery query, Resource resource, String queryParam) {
    configureQuery(query, resource, queryParam, Permissions.READ);
  }

  public void configureQuery(AbstractQuery query, Resource resource, String queryParam, Permission permission) {
    configureQuery(query);
    addAuthorizationCheckParameter(query, resource, queryParam, permission);
  }

  protected void addAuthorizationCheckParameter(AbstractQuery query, Resource resource, String queryParam, Permission permission) {
    if (isAuthorizationEnabled() && getCurrentAuthentication() != null) {
      PermissionCheck permCheck = new PermissionCheck();
      permCheck.setResource(resource);
      permCheck.setResourceIdQueryParam(queryParam);
      permCheck.setPermission(permission);

      query.addPermissionCheck(permCheck);
    }
  }

  // delete authorizations //////////////////////////////////////////////////

  public void deleteAuthorizationsByResourceId(Resource resource, String resourceId) {

    if(resourceId == null) {
      throw new IllegalArgumentException("Resource id cannot be null");
    }

    if(isAuthorizationEnabled()) {
      Map<String, Object> deleteParams = new HashMap<String, Object>();
      deleteParams.put("resourceType", resource.resourceType());
      deleteParams.put("resourceId", resourceId);
      getDbEntityManager().delete(AuthorizationEntity.class, "deleteAuthorizationsForResourceId", deleteParams);
    }

  }

  // predefined authorization checks

  /* PROCESS DEFINITION */

  // read permission //////////////////////////////////////////////////

  public void checkReadProcessDefinition(ProcessDefinitionEntity definition) {
    checkReadProcessDefinition(definition.getKey());
  }

  public void checkReadProcessDefinition(String processDefinitionKey) {
    checkAuthorization(READ, PROCESS_DEFINITION, processDefinitionKey);
  }

  // update permission ///////////////////////////////////////////////

  public void checkUpdateProcessDefinitionById(String processDefinitionId) {
    ProcessDefinitionEntity definition = getProcessDefinitionManager().findLatestProcessDefinitionById(processDefinitionId);
    String processDefinitionKey = definition.getKey();
    checkUpdateProcessDefinitionByKey(processDefinitionKey);
  }

  public void checkUpdateProcessDefinitionByKey(String processDefinitionKey) {
    checkAuthorization(UPDATE, PROCESS_DEFINITION, processDefinitionKey);
  }

  /* PROCESS INSTANCE */

  // create permission ///////////////////////////////////////////////////

  public void checkCreateProcessInstance(ProcessDefinitionEntity definition) {
    // necessary permissions:
    // - CREATE on PROCESS_INSTANCE
    // AND
    // - CREATE_INSTANCE on PROCESS_DEFINITION
    checkAuthorization(CREATE, PROCESS_INSTANCE);
    checkAuthorization(CREATE_INSTANCE, PROCESS_DEFINITION, definition.getKey());
  }

  // read permission ////////////////////////////////////////////////////

  public void checkReadProcessInstance(String processInstanceId) {
    ExecutionEntity execution = getProcessInstanceManager().findExecutionById(processInstanceId);
    if (execution != null) {
      checkReadProcessInstance(execution);
    }
  }

  public void checkReadProcessInstance(ExecutionEntity execution) {
    ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) execution.getProcessDefinition();

    // necessary permissions:
    // - READ on PROCESS_INSTANCE

    PermissionCheck firstCheck = new PermissionCheck();
    firstCheck.setPermission(READ);
    firstCheck.setResource(PROCESS_INSTANCE);
    firstCheck.setResourceId(execution.getProcessInstanceId());

    // ... OR ...

    // - READ_INSTANCE on PROCESS_DEFINITION
    PermissionCheck secondCheck = new PermissionCheck();
    secondCheck.setPermission(READ_INSTANCE);
    secondCheck.setResource(PROCESS_DEFINITION);
    secondCheck.setResourceId(processDefinition.getKey());
    secondCheck.setAuthorizationNotFoundReturnValue(0l);

    checkAuthorization(Arrays.asList(firstCheck, secondCheck));
  }

  // update permission //////////////////////////////////////////////////

  public void checkUpdateProcessInstanceById(String processInstanceId) {
    ExecutionEntity execution = getProcessInstanceManager().findExecutionById(processInstanceId);
    if (execution != null) {
      checkUpdateProcessInstance(execution);
    }
  }

  public void checkUpdateProcessInstance(ExecutionEntity execution) {
    ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) execution.getProcessDefinition();

    // necessary permissions:
    // - UPDATE on PROCESS_INSTANCE

    PermissionCheck firstCheck = new PermissionCheck();
    firstCheck.setPermission(UPDATE);
    firstCheck.setResource(PROCESS_INSTANCE);
    firstCheck.setResourceId(execution.getProcessInstanceId());

    // ... OR ...

    // - UPDATE_INSTANCE on PROCESS_DEFINITION

    PermissionCheck secondCheck = new PermissionCheck();
    secondCheck.setPermission(UPDATE_INSTANCE);
    secondCheck.setResource(PROCESS_DEFINITION);
    secondCheck.setResourceId(processDefinition.getKey());
    secondCheck.setAuthorizationNotFoundReturnValue(0l);

    checkAuthorization(Arrays.asList(firstCheck, secondCheck));
  }

  public void checkUpdateInstanceOnProcessDefinitionById(String processDefinitionId) {
    ProcessDefinitionEntity definition = getProcessDefinitionManager().findLatestProcessDefinitionById(processDefinitionId);
    if (definition != null) {
      String processDefinitionKey = definition.getKey();
      checkUpdateInstanceOnProcessDefinitionByKey(processDefinitionKey);
    }
  }

  public void checkUpdateInstanceOnProcessDefinitionByKey(String processDefinitionKey) {
    checkAuthorization(UPDATE_INSTANCE, PROCESS_DEFINITION, processDefinitionKey);
  }

  // delete permission /////////////////////////////////////////////////

  public void checkDeleteProcessInstance(ExecutionEntity execution) {
    ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) execution.getProcessDefinition();

    // necessary permissions:
    // - DELETE on PROCESS_INSTANCE

    PermissionCheck firstCheck = new PermissionCheck();
    firstCheck.setPermission(DELETE);
    firstCheck.setResource(PROCESS_INSTANCE);
    firstCheck.setResourceId(execution.getProcessInstanceId());

    // ... OR ...

    // - DELETE_INSTANCE on PROCESS_DEFINITION

    PermissionCheck secondCheck = new PermissionCheck();
    secondCheck.setPermission(DELETE_INSTANCE);
    secondCheck.setResource(PROCESS_DEFINITION);
    secondCheck.setResourceId(processDefinition.getKey());
    secondCheck.setAuthorizationNotFoundReturnValue(0l);

    checkAuthorization(Arrays.asList(firstCheck, secondCheck));
  }

  /* TASK */

  // create permission /////////////////////////////////////////////

  public void checkCreateTask() {
    checkAuthorization(CREATE, TASK);
  }

  // read permission //////////////////////////////////////////////

  public void checkReadTask(TaskEntity task) {
    String taskId = task.getId();

    String executionId = task.getExecutionId();
    if (executionId != null) {

      // if task exists in context of a process instance
      // then check the following permissions:
      // - READ on TASK
      // - READ_TASK on PROCESS_DEFINITION

      ExecutionEntity execution = task.getExecution();
      ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) execution.getProcessDefinition();

      PermissionCheck readPermissionCheck = new PermissionCheck();
      readPermissionCheck.setPermission(READ);
      readPermissionCheck.setResource(TASK);
      readPermissionCheck.setResourceId(taskId);

      PermissionCheck readTaskPermissionCheck = new PermissionCheck();
      readTaskPermissionCheck.setPermission(READ_TASK);
      readTaskPermissionCheck.setResource(PROCESS_DEFINITION);
      readTaskPermissionCheck.setResourceId(processDefinition.getKey());
      readTaskPermissionCheck.setAuthorizationNotFoundReturnValue(0l);

      checkAuthorization(Arrays.asList(readPermissionCheck, readTaskPermissionCheck));

    } else {

      // if task does not exist in context of process
      // instance, then it is either a (a) standalone task
      // or (b) it exists in context of a case instance.

      // (a) standalone task: check following permission
      // - READ on TASK
      // (b) task in context of a case instance, in this
      // case it is not necessary to check any permission,
      // because such tasks can always be read

      String caseExecutionId = task.getCaseExecutionId();
      if (caseExecutionId == null) {
        checkAuthorization(READ, TASK, taskId);
      }

    }
  }

  // update permission ////////////////////////////////////////////

  public void checkUpdateTask(TaskEntity task) {
    String taskId = task.getId();

    String executionId = task.getExecutionId();
    if (executionId != null) {

      // if task exists in context of a process instance
      // then check the following permissions:
      // - UPDATE on TASK
      // - UPDATE_TASK on PROCESS_DEFINITION

      ExecutionEntity execution = task.getExecution();
      ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) execution.getProcessDefinition();

      PermissionCheck updatePermissionCheck = new PermissionCheck();
      updatePermissionCheck.setPermission(UPDATE);
      updatePermissionCheck.setResource(TASK);
      updatePermissionCheck.setResourceId(taskId);

      PermissionCheck updateTaskPermissionCheck = new PermissionCheck();
      updateTaskPermissionCheck.setPermission(UPDATE_TASK);
      updateTaskPermissionCheck.setResource(PROCESS_DEFINITION);
      updateTaskPermissionCheck.setResourceId(processDefinition.getKey());
      updateTaskPermissionCheck.setAuthorizationNotFoundReturnValue(0l);

      checkAuthorization(Arrays.asList(updatePermissionCheck, updateTaskPermissionCheck));

    } else {

      // if task does not exist in context of process
      // instance, then it is either a (a) standalone task
      // or (b) it exists in context of a case instance.

      // (a) standalone task: check following permission
      // - READ on TASK
      // (b) task in context of a case instance, in this
      // case it is not necessary to check any permission,
      // because such tasks can always be updated

      String caseExecutionId = task.getCaseExecutionId();
      if (caseExecutionId == null) {
        checkAuthorization(UPDATE, TASK, taskId);
      }

    }
  }

  // delete permission ////////////////////////////////////////

  public void checkDeleteTask(TaskEntity task) {
    String taskId = task.getId();

    // Note: Calling TaskService#deleteTask() to
    // delete a task which exists in context of
    // a process instance or case instance cannot
    // be deleted. In such a case TaskService#deleteTask()
    // throws an exception before invoking the
    // authorization check.

    String executionId = task.getExecutionId();
    String caseExecutionId = task.getCaseExecutionId();

    if (executionId == null && caseExecutionId == null) {
      checkAuthorization(DELETE, TASK, taskId);
    }
  }

  /* QUERIES */

  // process definition query ////////////////////////////////

  public void configureProcessDefinitionQuery(ProcessDefinitionQueryImpl query) {
    configureQuery(query, PROCESS_DEFINITION, "RES.KEY_");
  }

  // execution/process instance query ////////////////////////

  public void configureExecutionQuery(AbstractQuery query) {
    configureQuery(query);
    addAuthorizationCheckParameter(query, PROCESS_INSTANCE, "RES.PROC_INST_ID_", READ);
    addAuthorizationCheckParameter(query, PROCESS_DEFINITION, "P.KEY_", READ_INSTANCE);
  }

  // task query //////////////////////////////////////////////

  public void configureTaskQuery(TaskQueryImpl query) {
    query.getPermissionChecks().clear();
    query.getTaskPermissionChecks().clear();

    Authentication currentAuthentication = getCurrentAuthentication();
    if(isAuthorizationEnabled() && currentAuthentication != null) {

      // necessary authorization check when the task is part of
      // a running process instance
      configureQuery(query);
      addAuthorizationCheckParameter(query, TASK, "RES.ID_", READ);
      addAuthorizationCheckParameter(query, PROCESS_DEFINITION, "PROCDEF.KEY_", READ_TASK);

      // necessary authorization check when the task is not part
      // of running process or case instance
      PermissionCheck standaloneTaskPermissionCheck = new PermissionCheck();
      standaloneTaskPermissionCheck.setPermission(READ);
      standaloneTaskPermissionCheck.setResource(TASK);
      standaloneTaskPermissionCheck.setResourceIdQueryParam("RES.ID_");
      standaloneTaskPermissionCheck.setAuthorizationNotFoundReturnValue(0l);

      query.addTaskPermissionCheck(standaloneTaskPermissionCheck);
    }
  }

  // event subscription query //////////////////////////////

  public void configureEventSubscriptionQuery(EventSubscriptionQueryImpl query) {
    configureQuery(query);
    addAuthorizationCheckParameter(query, PROCESS_INSTANCE, "RES.PROC_INST_ID_", READ);
    addAuthorizationCheckParameter(query, PROCESS_DEFINITION, "PROCDEF.KEY_", READ_INSTANCE);
  }

  // incident query ///////////////////////////////////////

  public void configureIncidentQuery(IncidentQueryImpl query) {
    configureQuery(query);
    addAuthorizationCheckParameter(query, PROCESS_INSTANCE, "RES.PROC_INST_ID_", READ);
    addAuthorizationCheckParameter(query, PROCESS_DEFINITION, "PROCDEF.KEY_", READ_INSTANCE);
  }

  // variable instance query /////////////////////////////

  protected void configureVariableInstanceQuery(VariableInstanceQueryImpl query) {
    query.getPermissionChecks().clear();
    query.getTaskPermissionChecks().clear();

    Authentication currentAuthentication = getCurrentAuthentication();
    if(isAuthorizationEnabled() && currentAuthentication != null) {

      // necessary authorization check when the variable instance is part of
      // a running process instance
      configureQuery(query);
      addAuthorizationCheckParameter(query, PROCESS_INSTANCE, "RES.PROC_INST_ID_", READ);
      addAuthorizationCheckParameter(query, PROCESS_DEFINITION, "PROCDEF.KEY_", READ_INSTANCE);

      // necessary authorization check when the variable instance is part
      // of a standalone task
      PermissionCheck taskPermissionCheck = new PermissionCheck();
      taskPermissionCheck.setResource(TASK);
      taskPermissionCheck.setPermission(READ);
      taskPermissionCheck.setResourceIdQueryParam("RES.TASK_ID_");
      taskPermissionCheck.setAuthorizationNotFoundReturnValue(0l);

      query.addTaskPermissionCheck(taskPermissionCheck);
    }
  }

  /* STATISTICS QUERY */

  public void configureProcessDefinitionStatisticsQuery(ProcessDefinitionStatisticsQueryImpl query) {
    configureQuery(query, PROCESS_DEFINITION, "PROCDEF.KEY_");

    query.getProcessInstancePermissionChecks().clear();
    query.getJobPermissionChecks().clear();
    query.getIncidentPermissionChecks().clear();

    PermissionCheck firstProcessInstancePermissionCheck = new PermissionCheck();
    firstProcessInstancePermissionCheck.setResource(PROCESS_INSTANCE);
    firstProcessInstancePermissionCheck.setPermission(READ);
    firstProcessInstancePermissionCheck.setResourceIdQueryParam("E.PROC_INST_ID_");

    PermissionCheck secondProcessInstancePermissionCheck = new PermissionCheck();
    secondProcessInstancePermissionCheck.setResource(PROCESS_DEFINITION);
    secondProcessInstancePermissionCheck.setPermission(READ_INSTANCE);
    secondProcessInstancePermissionCheck.setResourceIdQueryParam("P.KEY_");
    secondProcessInstancePermissionCheck.setAuthorizationNotFoundReturnValue(0l);

    query.addProcessInstancePermissionCheck(firstProcessInstancePermissionCheck);
    query.addProcessInstancePermissionCheck(secondProcessInstancePermissionCheck);

    if (query.isFailedJobsToInclude()) {
      PermissionCheck firstJobPermissionCheck = new PermissionCheck();
      firstJobPermissionCheck.setResource(PROCESS_INSTANCE);
      firstJobPermissionCheck.setPermission(READ);
      firstJobPermissionCheck.setResourceIdQueryParam("PROCESS_INSTANCE_ID_");

      PermissionCheck secondJobPermissionCheck = new PermissionCheck();
      secondJobPermissionCheck.setResource(PROCESS_DEFINITION);
      secondJobPermissionCheck.setPermission(READ_INSTANCE);
      secondJobPermissionCheck.setResourceIdQueryParam("PROCESS_DEF_KEY_");
      secondJobPermissionCheck.setAuthorizationNotFoundReturnValue(0l);

      query.addJobPermissionCheck(firstJobPermissionCheck);
      query.addJobPermissionCheck(secondJobPermissionCheck);
    }

    if (query.isIncidentsToInclude()) {
      PermissionCheck firstIncidentPermissionCheck = new PermissionCheck();
      firstIncidentPermissionCheck.setResource(PROCESS_INSTANCE);
      firstIncidentPermissionCheck.setPermission(READ);
      firstIncidentPermissionCheck.setResourceIdQueryParam("I.PROC_INST_ID_");

      PermissionCheck secondIncidentPermissionCheck = new PermissionCheck();
      secondIncidentPermissionCheck.setResource(PROCESS_DEFINITION);
      secondIncidentPermissionCheck.setPermission(READ_INSTANCE);
      secondIncidentPermissionCheck.setResourceIdQueryParam("PROCDEF.KEY_");
      secondIncidentPermissionCheck.setAuthorizationNotFoundReturnValue(0l);

      query.addIncidentPermissionCheck(firstIncidentPermissionCheck);
      query.addIncidentPermissionCheck(secondIncidentPermissionCheck);

    }
  }

  public void configureActivityStatisticsQuery(ActivityStatisticsQueryImpl query) {
    configureQuery(query);

    query.getProcessInstancePermissionChecks().clear();
    query.getJobPermissionChecks().clear();
    query.getIncidentPermissionChecks().clear();

    PermissionCheck firstProcessInstancePermissionCheck = new PermissionCheck();
    firstProcessInstancePermissionCheck.setResource(PROCESS_INSTANCE);
    firstProcessInstancePermissionCheck.setPermission(READ);
    firstProcessInstancePermissionCheck.setResourceIdQueryParam("E.PROC_INST_ID_");

    PermissionCheck secondProcessInstancePermissionCheck = new PermissionCheck();
    secondProcessInstancePermissionCheck.setResource(PROCESS_DEFINITION);
    secondProcessInstancePermissionCheck.setPermission(READ_INSTANCE);
    secondProcessInstancePermissionCheck.setResourceIdQueryParam("P.KEY_");
    secondProcessInstancePermissionCheck.setAuthorizationNotFoundReturnValue(0l);

    query.addProcessInstancePermissionCheck(firstProcessInstancePermissionCheck);
    query.addProcessInstancePermissionCheck(secondProcessInstancePermissionCheck);

    if (query.isFailedJobsToInclude()) {
      PermissionCheck firstJobPermissionCheck = new PermissionCheck();
      firstJobPermissionCheck.setResource(PROCESS_INSTANCE);
      firstJobPermissionCheck.setPermission(READ);
      firstJobPermissionCheck.setResourceIdQueryParam("JOB.PROCESS_INSTANCE_ID_");

      PermissionCheck secondJobPermissionCheck = new PermissionCheck();
      secondJobPermissionCheck.setResource(PROCESS_DEFINITION);
      secondJobPermissionCheck.setPermission(READ_INSTANCE);
      secondJobPermissionCheck.setResourceIdQueryParam("JOB.PROCESS_DEF_KEY_");
      secondJobPermissionCheck.setAuthorizationNotFoundReturnValue(0l);

      query.addJobPermissionCheck(firstJobPermissionCheck);
      query.addJobPermissionCheck(secondJobPermissionCheck);
    }

    if (query.isIncidentsToInclude()) {
      PermissionCheck firstIncidentPermissionCheck = new PermissionCheck();
      firstIncidentPermissionCheck.setResource(PROCESS_INSTANCE);
      firstIncidentPermissionCheck.setPermission(READ);
      firstIncidentPermissionCheck.setResourceIdQueryParam("I.PROC_INST_ID_");

      PermissionCheck secondIncidentPermissionCheck = new PermissionCheck();
      secondIncidentPermissionCheck.setResource(PROCESS_DEFINITION);
      secondIncidentPermissionCheck.setPermission(READ_INSTANCE);
      secondIncidentPermissionCheck.setResourceIdQueryParam("PROCDEF.KEY_");
      secondIncidentPermissionCheck.setAuthorizationNotFoundReturnValue(0l);

      query.addIncidentPermissionCheck(firstIncidentPermissionCheck);
      query.addIncidentPermissionCheck(secondIncidentPermissionCheck);

    }
  }

}
