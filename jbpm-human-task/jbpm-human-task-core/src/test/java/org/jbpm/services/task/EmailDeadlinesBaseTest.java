/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.jbpm.services.task;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;

import org.jbpm.services.task.impl.factories.TaskFactory;
import org.jbpm.services.task.utils.ContentMarshallerHelper;
import org.junit.Test;
import org.kie.api.task.model.OrganizationalEntity;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.User;
import org.kie.internal.task.api.TaskModelProvider;
import org.kie.internal.task.api.model.ContentData;
import org.kie.internal.task.api.model.InternalContent;
import org.kie.internal.task.api.model.InternalOrganizationalEntity;
import org.kie.internal.task.api.model.InternalPeopleAssignments;
import org.kie.internal.task.api.model.InternalTask;
import org.kie.internal.task.api.model.InternalTaskData;
import org.kie.internal.utils.ChainedProperties;
import org.kie.internal.utils.ClassLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;


/**
 * All users are defined as part of userinfo.propeties file that is utilized by PropertyUserInfoImpl
 *
 */

public abstract class EmailDeadlinesBaseTest extends HumanTaskServicesBaseTest {
    private static final Logger logger = LoggerFactory.getLogger(EmailDeadlinesBaseTest.class);
    
    private Wiser wiser;

    public void setup() {
        final ChainedProperties props =
                new ChainedProperties( "email.conf", ClassLoaderUtil.getClassLoader( null, getClass(), false ));

        wiser = new Wiser();
        wiser.setHostname(props.getProperty( "mail.smtp.host", "localhost" ));
        wiser.setPort(Integer.parseInt(props.getProperty("mail.smtp.port", "2345")));
        wiser.start();
        try {
        	Thread.sleep(1000);
        } catch (Throwable t) {
        	// Do nothing
        }
    }
    
    
    public void tearDown(){
        if (wiser != null) {
            wiser.stop();
            try {
            	Thread.sleep(1000);
            } catch (Throwable t) {
            	// Do nothing
            }
        }
        super.tearDown();
    }

    protected Wiser getWiser() {
        return this.wiser;
    }

    @Test    
    public void testDelayedEmailNotificationOnDeadline() throws Exception {        
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("now", new Date());

        Reader reader = new InputStreamReader(getClass().getResourceAsStream(MvelFilePath.DeadlineWithNotification));
        Task task = (Task) TaskFactory.evalTask(reader, vars);
        
        taskService.addTask(task, new HashMap<String, Object>());
        long taskId = task.getId();

        InternalContent content = (InternalContent) TaskModelProvider.getFactory().newContent();
        
        Map<String, String> params = fillMarshalSubjectAndBodyParams();
        ContentData marshalledObject = ContentMarshallerHelper.marshal(params, null);
        content.setContent(marshalledObject.getContent());
        taskService.addContent(taskId, content);
        long contentId = content.getId();
        
        content = (InternalContent) taskService.getContentById(contentId);
        Object unmarshallObject = ContentMarshallerHelper.unmarshall(content.getContent(), null);
        checkContentSubjectAndBody(unmarshallObject);

        // emails should not be set yet
        assertEquals(0, getWiser().getMessages().size());
        Thread.sleep(100);

        // nor yet
        assertEquals(0, getWiser().getMessages().size());

        long time = 0;
        while (getWiser().getMessages().size() < 2 && time < 5000) {
            Thread.sleep(50);
            time += 50;
        }
        for (WiserMessage msg : getWiser().getMessages()) {
            logger.info(msg.getEnvelopeReceiver());
        }
        // 1 email with two recipients should now exist
        assertEquals(2, getWiser().getMessages().size());

        List<String> list = new ArrayList<String>(2);
        list.add(getWiser().getMessages().get(0).getEnvelopeReceiver());
        list.add(getWiser().getMessages().get(1).getEnvelopeReceiver());

        assertTrue(list.contains("tony@domain.com"));
        assertTrue(list.contains("darth@domain.com"));

        MimeMessage msg = ((WiserMessage) getWiser().getMessages().get(0)).getMimeMessage();
        assertEquals(myBody, msg.getContent());
        assertEquals(mySubject, msg.getSubject());
        assertEquals("from@domain.com", ((InternetAddress) msg.getFrom()[0]).getAddress());
        assertEquals("replyTo@domain.com", ((InternetAddress) msg.getReplyTo()[0]).getAddress());
        assertEquals("tony@domain.com", ((InternetAddress) msg.getRecipients(RecipientType.TO)[0]).getAddress());
        assertEquals("darth@domain.com", ((InternetAddress) msg.getRecipients(RecipientType.TO)[1]).getAddress());
    }
    
    @Test    
    public void testDelayedEmailNotificationOnDeadlineContentSingleObject() throws Exception {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("now", new Date());

        Reader reader = new InputStreamReader(getClass().getResourceAsStream(MvelFilePath.DeadlineWithNotificationContentSingleObject));
        Task task = (Task) TaskFactory.evalTask(reader, vars);
     
        taskService.addTask(task, new HashMap<String, Object>());
        long taskId = task.getId();

        InternalContent content = (InternalContent) TaskModelProvider.getFactory().newContent();
        ContentData marshalledObject = ContentMarshallerHelper.marshal("'singleobject'", null);
        content.setContent(marshalledObject.getContent());
       
        taskService.addContent(taskId, content);
        long contentId = content.getId();
      
        content = (InternalContent) taskService.getContentById(contentId);
        Object unmarshallObject = ContentMarshallerHelper.unmarshall(content.getContent(), null);
        assertEquals("'singleobject'", unmarshallObject.toString());

        // emails should not be set yet
        assertEquals(0, getWiser().getMessages().size());
        Thread.sleep(100);
        // nor yet
        assertEquals(0, getWiser().getMessages().size());
        long time = 0;
        while (getWiser().getMessages().size() < 2 && time < 5000) {
            Thread.sleep(500);
            time += 500;
        }

        // 1 email with two recipients should now exist
        assertEquals(2, getWiser().getMessages().size());
        List<String> list = new ArrayList<String>(2);
        list.add(getWiser().getMessages().get(0).getEnvelopeReceiver());
        list.add(getWiser().getMessages().get(1).getEnvelopeReceiver());

        assertTrue(list.contains("tony@domain.com"));
        assertTrue(list.contains("darth@domain.com"));

        MimeMessage msg = ((WiserMessage) getWiser().getMessages().get(0)).getMimeMessage();
        assertEquals("'singleobject'", msg.getContent());
        assertEquals("'singleobject'", msg.getSubject());
        assertEquals("from@domain.com", ((InternetAddress) msg.getFrom()[0]).getAddress());
        assertEquals("replyTo@domain.com", ((InternetAddress) msg.getReplyTo()[0]).getAddress());
        assertEquals("tony@domain.com", ((InternetAddress) msg.getRecipients(RecipientType.TO)[0]).getAddress());
        assertEquals("darth@domain.com", ((InternetAddress) msg.getRecipients(RecipientType.TO)[1]).getAddress());
    }

    @Test    
    public void testDelayedEmailNotificationOnDeadlineTaskCompleted() throws Exception {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("now", new Date());

        Reader reader = new InputStreamReader(getClass().getResourceAsStream(MvelFilePath.DeadlineWithNotification));
        InternalTask task = (InternalTask) TaskFactory.evalTask(reader, vars);
        
        ((InternalTaskData) task.getTaskData()).setSkipable(true);
        InternalPeopleAssignments assignments = (InternalPeopleAssignments) TaskModelProvider.getFactory().newPeopleAssignments();
        List<OrganizationalEntity> ba = new ArrayList<OrganizationalEntity>();
        User user = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user).setId("Administrator");
        ba.add(user);
        assignments.setBusinessAdministrators(ba);
        
        List<OrganizationalEntity> po = new ArrayList<OrganizationalEntity>();
        User user2 = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user2).setId("Administrator");        
        po.add(user2);
        assignments.setPotentialOwners(po);
        
        task.setPeopleAssignments(assignments);
        
        taskService.addTask(task, new HashMap<String, Object>());
        long taskId = task.getId();

        InternalContent content = (InternalContent) TaskModelProvider.getFactory().newContent();
        
        Map<String, String> params = fillMarshalSubjectAndBodyParams();
        ContentData marshalledObject = ContentMarshallerHelper.marshal(params, null);
        content.setContent(marshalledObject.getContent());
        taskService.addContent(taskId, content);
        long contentId = content.getId();
        
        content = (InternalContent) taskService.getContentById(contentId);
        Object unmarshallObject = ContentMarshallerHelper.unmarshall(content.getContent(), null);
        checkContentSubjectAndBody(unmarshallObject);
        
        taskService.start(taskId, "Administrator");
        taskService.complete(taskId, "Administrator", null);
        // emails should not be set yet
        assertEquals(0, getWiser().getMessages().size());
        Thread.sleep(100);

        // nor yet
        assertEquals(0, getWiser().getMessages().size());

        long time = 0;
        while (getWiser().getMessages().size() < 2 && time < 5000) {
            Thread.sleep(500);
            time += 500;
        }

        // no email should ne sent as task was completed before deadline was triggered
        assertEquals(0, getWiser().getMessages().size());
        task = (InternalTask) taskService.getTaskById(taskId);
        assertEquals(Status.Completed, task.getTaskData().getStatus());
        assertEquals(0, task.getDeadlines().getStartDeadlines().size());
        assertEquals(0, task.getDeadlines().getEndDeadlines().size());       
    }

    @Test    
    public void testDelayedEmailNotificationOnDeadlineTaskFailed() throws Exception {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("now", new Date());

        Reader reader = new InputStreamReader(getClass().getResourceAsStream(MvelFilePath.DeadlineWithNotification));
        InternalTask task = (InternalTask) TaskFactory.evalTask(reader, vars);
        
        ((InternalTaskData) task.getTaskData()).setSkipable(true);
        InternalPeopleAssignments assignments = (InternalPeopleAssignments) TaskModelProvider.getFactory().newPeopleAssignments();
        List<OrganizationalEntity> ba = new ArrayList<OrganizationalEntity>();
        User user = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user).setId("Administrator");
        ba.add(user);
        assignments.setBusinessAdministrators(ba);
        
        List<OrganizationalEntity> po = new ArrayList<OrganizationalEntity>();
        User user2 = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user2).setId("Administrator");        
        po.add(user2);
        assignments.setPotentialOwners(po);
        
        task.setPeopleAssignments(assignments);
        
        
        taskService.addTask(task, new HashMap<String, Object>());
        long taskId = task.getId();

        InternalContent content = (InternalContent) TaskModelProvider.getFactory().newContent();
        
        Map<String, String> params = fillMarshalSubjectAndBodyParams();
        ContentData marshalledObject = ContentMarshallerHelper.marshal(params, null);
        content.setContent(marshalledObject.getContent());
        taskService.addContent(taskId, content);
        long contentId = content.getId();
        
        content = (InternalContent) taskService.getContentById(contentId);
        Object unmarshallObject = ContentMarshallerHelper.unmarshall(content.getContent(), null);
        checkContentSubjectAndBody(unmarshallObject);
        
        taskService.start(taskId, "Administrator");
        taskService.fail(taskId, "Administrator", null);
        // emails should not be set yet
        assertEquals(0, getWiser().getMessages().size());
        Thread.sleep(100);

        // nor yet
        assertEquals(0, getWiser().getMessages().size());

        long time = 0;
        while (getWiser().getMessages().size() < 2 && time < 5000) {
            Thread.sleep(500);
            time += 500;
        }

        // no email should ne sent as task was completed before deadline was triggered
        assertEquals(0, getWiser().getMessages().size());
        task = (InternalTask) taskService.getTaskById(taskId);
        assertEquals(Status.Failed, task.getTaskData().getStatus());
        assertEquals(0, task.getDeadlines().getStartDeadlines().size());
        assertEquals(0, task.getDeadlines().getEndDeadlines().size());
    }

    @Test    
    public void testDelayedEmailNotificationOnDeadlineTaskSkipped() throws Exception {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("now", new Date());

        Reader reader = new InputStreamReader(getClass().getResourceAsStream(MvelFilePath.DeadlineWithNotification));
        InternalTask task = (InternalTask) TaskFactory.evalTask(reader, vars);
        
        ((InternalTaskData) task.getTaskData()).setSkipable(true);
        InternalPeopleAssignments assignments = (InternalPeopleAssignments) TaskModelProvider.getFactory().newPeopleAssignments();
        List<OrganizationalEntity> ba = new ArrayList<OrganizationalEntity>();
        User user = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user).setId("Administrator");
        ba.add(user);
        assignments.setBusinessAdministrators(ba);
        
        List<OrganizationalEntity> po = new ArrayList<OrganizationalEntity>();
        User user2 = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user2).setId("Administrator");        
        po.add(user2);
        assignments.setPotentialOwners(po);
        
        task.setPeopleAssignments(assignments);
        taskService.addTask(task, new HashMap<String, Object>());
        long taskId = task.getId();

        InternalContent content = (InternalContent) TaskModelProvider.getFactory().newContent();
        
        Map<String, String> params = fillMarshalSubjectAndBodyParams();
        ContentData marshalledObject = ContentMarshallerHelper.marshal(params, null);
        content.setContent(marshalledObject.getContent());
        taskService.addContent(taskId, content);
        long contentId = content.getId();
        
        content = (InternalContent) taskService.getContentById(contentId);
        Object unmarshallObject = ContentMarshallerHelper.unmarshall(content.getContent(), null);
        checkContentSubjectAndBody(unmarshallObject);
        
        taskService.skip(taskId, "Administrator");
        // emails should not be set yet
        assertEquals(0, getWiser().getMessages().size());
        Thread.sleep(100);

        // nor yet
        assertEquals(0, getWiser().getMessages().size());

        long time = 0;
        while (getWiser().getMessages().size() < 2 && time < 5000) {
            Thread.sleep(500);
            time += 500;
        }

        // no email should ne sent as task was completed before deadline was triggered
        assertEquals(0, getWiser().getMessages().size());
        task = (InternalTask) taskService.getTaskById(taskId);
        assertEquals(Status.Obsolete, task.getTaskData().getStatus());
        assertEquals(0, task.getDeadlines().getStartDeadlines().size());
        assertEquals(0, task.getDeadlines().getEndDeadlines().size());
    }

    @Test        
    public void testDelayedEmailNotificationOnDeadlineTaskExited() throws Exception {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("now", new Date());

        Reader reader = new InputStreamReader(getClass().getResourceAsStream(MvelFilePath.DeadlineWithNotification));
        InternalTask task = (InternalTask) TaskFactory.evalTask(reader, vars);
        
        ((InternalTaskData) task.getTaskData()).setSkipable(true);
        InternalPeopleAssignments assignments = (InternalPeopleAssignments) TaskModelProvider.getFactory().newPeopleAssignments();
        List<OrganizationalEntity> ba = new ArrayList<OrganizationalEntity>();
        User user = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user).setId("Administrator");
        ba.add(user);
        assignments.setBusinessAdministrators(ba);
        
        List<OrganizationalEntity> po = new ArrayList<OrganizationalEntity>();
        User user2 = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user2).setId("Administrator");        
        po.add(user2);
        assignments.setPotentialOwners(po);
        
        task.setPeopleAssignments(assignments);
        taskService.addTask(task, new HashMap<String, Object>());
        long taskId = task.getId();

        InternalContent content = (InternalContent) TaskModelProvider.getFactory().newContent();
        
        Map<String, String> params = fillMarshalSubjectAndBodyParams();
        ContentData marshalledObject = ContentMarshallerHelper.marshal(params, null);
        content.setContent(marshalledObject.getContent());
        taskService.addContent(taskId, content);
        long contentId = content.getId();
        
        content = (InternalContent) taskService.getContentById(contentId);
        Object unmarshallObject = ContentMarshallerHelper.unmarshall(content.getContent(), null);
        checkContentSubjectAndBody(unmarshallObject);
        
        taskService.exit(taskId, "Administrator");
        // emails should not be set yet
        assertEquals(0, getWiser().getMessages().size());
        Thread.sleep(100);

        // nor yet
        assertEquals(0, getWiser().getMessages().size());

        long time = 0;
        while (getWiser().getMessages().size() < 2 && time < 5000) {
            Thread.sleep(500);
            time += 500;
        }

        // no email should ne sent as task was completed before deadline was triggered
        assertEquals(0, getWiser().getMessages().size());
        task = (InternalTask) taskService.getTaskById(taskId);
        assertEquals(Status.Exited, task.getTaskData().getStatus());
        assertEquals(0, task.getDeadlines().getStartDeadlines().size());
        assertEquals(0, task.getDeadlines().getEndDeadlines().size());
    }

    @Test    
    public void testDelayedReassignmentOnDeadline() throws Exception {


        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("now", new Date());

        Reader reader = new InputStreamReader(getClass().getResourceAsStream(MvelFilePath.DeadlineWithReassignment));
        Task task = (Task) TaskFactory.evalTask(reader, vars);
        taskService.addTask(task, new HashMap<String, Object>());
        long taskId = task.getId();

        // Shouldn't have re-assigned yet
        Thread.sleep(1000);
        
        
        task = taskService.getTaskById(taskId);
        List<OrganizationalEntity> potentialOwners = (List<OrganizationalEntity>) task.getPeopleAssignments().getPotentialOwners();
        List<String> ids = new ArrayList<String>(potentialOwners.size());
        for (OrganizationalEntity entity : potentialOwners) {
            ids.add(entity.getId());
        }
        assertTrue(ids.contains("Tony Stark"));
        assertTrue(ids.contains("Luke Cage"));

        // should have re-assigned by now
        long time = 0;
        while (getWiser().getMessages().size() < 2 && time < 5000) {
            Thread.sleep(500);
            time += 500;
        }
        
        task = taskService.getTaskById(taskId);
        assertEquals(Status.Ready, task.getTaskData().getStatus());
        potentialOwners = (List<OrganizationalEntity>) task.getPeopleAssignments().getPotentialOwners();

        ids = new ArrayList<String>(potentialOwners.size());
        for (OrganizationalEntity entity : potentialOwners) {
            ids.add(entity.getId());
        }
        assertTrue(ids.contains("Bobba Fet"));
        assertTrue(ids.contains("Jabba Hutt"));
    }

    @Test    
    public void testDelayedEmailNotificationStartDeadlineStatusDoesNotMatch() throws Exception {
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("now", new Date());

        Reader reader = new InputStreamReader(getClass().getResourceAsStream(MvelFilePath.DeadlineWithNotification));
        InternalTask task = (InternalTask) TaskFactory.evalTask(reader, vars);
          
        ((InternalTaskData) task.getTaskData()).setSkipable(true);
        InternalPeopleAssignments assignments = (InternalPeopleAssignments) TaskModelProvider.getFactory().newPeopleAssignments();
        List<OrganizationalEntity> ba = new ArrayList<OrganizationalEntity>();
        User user = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user).setId("Administrator");
        ba.add(user);
        assignments.setBusinessAdministrators(ba);
          
        List<OrganizationalEntity> po = new ArrayList<OrganizationalEntity>();
        User user2 = TaskModelProvider.getFactory().newUser();
        ((InternalOrganizationalEntity) user2).setId("Administrator");        
        po.add(user2);
        assignments.setPotentialOwners(po);
        
        task.setPeopleAssignments(assignments);
        
        taskService.addTask(task, new HashMap<String, Object>());
        long taskId = task.getId();
        InternalContent content = (InternalContent) TaskModelProvider.getFactory().newContent();
        
        Map<String, String> params = fillMarshalSubjectAndBodyParams();
        ContentData marshalledObject = ContentMarshallerHelper.marshal(params, null);
        content.setContent(marshalledObject.getContent());
        taskService.addContent(taskId, content);
        long contentId = content.getId();
        
        content = (InternalContent) taskService.getContentById(contentId);
        Object unmarshallObject = ContentMarshallerHelper.unmarshall(content.getContent(), null);
        checkContentSubjectAndBody(unmarshallObject);
        
        taskService.start(taskId, "Administrator");
        
        // emails should not be set yet
        assertEquals(0, getWiser().getMessages().size());
        Thread.sleep(100);
        // nor yet
        assertEquals(0, getWiser().getMessages().size());

        long time = 0;
        while (getWiser().getMessages().size() < 2 && time < 5000) {
            Thread.sleep(500);
            time += 500;
        }
        // no email should ne sent as task was completed before deadline was triggered
        assertEquals(0, getWiser().getMessages().size());
        task = (InternalTask) taskService.getTaskById(taskId);
        assertEquals(Status.InProgress, task.getTaskData().getStatus());
        assertEquals(0, task.getDeadlines().getStartDeadlines().size());
        assertEquals(0, task.getDeadlines().getEndDeadlines().size());
    }
}
