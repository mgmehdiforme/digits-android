/*
 * Copyright (C) 2015 Twitter, Inc.
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
 *
 */

package com.digits.sdk.android;

import android.content.ComponentName;
import android.content.Intent;
import android.test.mock.MockContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ContactsClientTests {
    private MockContext context;
    private ContactsClient contactsClient;
    private DigitsApiClientManager apiClientManager;
    private ApiInterface sdkService;
    private ComponentName activityComponent;
    private ComponentName serviceComponent;
    private ContactsCallback callback;

    ArgumentCaptor<ContactsClient.FoundContactsCallbackWrapper> callbackCaptor;

    private DigitsEventCollector digitsEventCollector;

    final SandboxConfig sandboxConfig = new SandboxConfig();
    final Digits digits = mock(Digits.class);
    final DigitsUserAgent userAgent = new DigitsUserAgent("digitsVersion", "androidVersion",
            "appName");
    private ContactsPreferenceManager prefManager;
    private ActivityClassManagerFactory activityClassManagerFactory;

    ArgumentCaptor<Intent> intentArgumentCaptor;

    @Before
    public void setUp() throws Exception {
        activityClassManagerFactory = new ActivityClassManagerFactory();
        context = mock(MockContext.class);
        callback = mock(ContactsCallback.class);

        prefManager = mock(ContactsPreferenceManager.class);
        digitsEventCollector = mock(DigitsEventCollector.class);
        callbackCaptor = ArgumentCaptor.forClass(ContactsClient.FoundContactsCallbackWrapper.class);

        intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        when(digits.getContext()).thenReturn(context);
        when(context.getPackageName()).thenReturn(getClass().getPackage().toString());
        when(digits.getActivityClassManager()).thenReturn(new ActivityClassManagerImp());

        sdkService = mock(ApiInterface.class);
        apiClientManager = mock(DigitsApiClientManager.class);
        when(apiClientManager.getService()).thenReturn(sdkService);

        contactsClient = new ContactsClient(digits, apiClientManager, prefManager,
                activityClassManagerFactory, sandboxConfig, digitsEventCollector);

        activityComponent = new ComponentName(context, ContactsActivity.class.getName());
        serviceComponent = new ComponentName(context, ContactsUploadService.class.getName());

    }


    @Test
    public void testStartContactsUpload_noParams() {
        contactsClient = spy(contactsClient);

        contactsClient.startContactsUpload();

        verify(contactsClient).startContactsUpload(R.style.Digits_default);
        verify(digitsEventCollector).startContactsUpload(any(ContactsUploadStartDetails.class));
    }

    @Test
    public void testStartContactsUpload_oneParam() {
        contactsClient = spy(contactsClient);

        contactsClient.startContactsUpload(R.style.Digits_default);

        verify(contactsClient).startContactsUpload(context, R.style.Digits_default);
        verify(digitsEventCollector).startContactsUpload(any(ContactsUploadStartDetails.class));
    }

    @Test
    public void testStartContactsUpload_uploadPermissionNotGranted() {
        when(prefManager.hasContactImportPermissionGranted()).thenReturn(false);

        contactsClient.startContactsUpload(context, R.style.Digits_default);

        verify(context).startActivity(intentArgumentCaptor.capture());
        final Intent capturedIntent = intentArgumentCaptor.getValue();
        assertEquals(activityComponent, capturedIntent.getComponent());
        assertEquals(R.style.Digits_default,
                capturedIntent.getIntExtra(ThemeUtils.THEME_RESOURCE_ID, 0));
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, capturedIntent.getFlags());
        verify(prefManager).hasContactImportPermissionGranted();
        verify(digitsEventCollector, times(0)).startContactsUpload(
                any(ContactsUploadStartDetails.class));
    }

    @Test
    public void testStartContactsUpload_uploadPermissionGranted() {
        when(prefManager.hasContactImportPermissionGranted()).thenReturn(true);

        contactsClient.startContactsUpload(context, R.style.Digits_default);

        //verify start service is called, passing an ArgumentCaptor to get the intent and check
        // if it's correctly build
        verify(context).startService(intentArgumentCaptor.capture());
        final Intent capturedIntent = intentArgumentCaptor.getValue();
        assertEquals(serviceComponent, capturedIntent.getComponent());
        verify(prefManager).hasContactImportPermissionGranted();
    }

    @Test
    public void testHasUserGrantedPermission_uploadPermissionNotGranted() {
        when(prefManager.hasContactImportPermissionGranted()).thenReturn(false);

        assertFalse(contactsClient.hasUserGrantedPermission());
    }

    @Test
    public void testHasUserGrantedPermission_uploadPermissionGranted() {
        when(prefManager.hasContactImportPermissionGranted()).thenReturn(true);

        assertTrue(contactsClient.hasUserGrantedPermission());
    }

    @Test
    public void testDeleteAllContacts() {
        final ArgumentCaptor<ContactsClient.DeleteContactsCallbackWrapper> deleteCaptor =
                ArgumentCaptor.forClass(ContactsClient.DeleteContactsCallbackWrapper.class);

        contactsClient.deleteAllUploadedContacts(callback);

        verify(sdkService).deleteAll(deleteCaptor.capture());
        assertNotNull(deleteCaptor.getValue());

        verify(digitsEventCollector).startDeleteContacts(any(ContactsDeletionStartDetails.class));
    }

    @Test
    public void testGetContactMatches() {
        final String cursor = "";
        final Integer count = 20;

        contactsClient.lookupContactMatches(cursor, count, callback);

        verify(sdkService).usersAndUploadedBy(eq(cursor), eq(count), callbackCaptor.capture());
        assertNotNull(callbackCaptor.getValue());
        verify(digitsEventCollector).startFindMatches(any(ContactsLookupStartDetails.class));
    }

    @Test
    public void testGetContactMatches_countBelowMin() {
        final String cursor = "";
        final Integer count = 0;

        contactsClient.lookupContactMatches(cursor, count, callback);

        verify(sdkService).usersAndUploadedBy(eq(cursor), eq((Integer) null),
                callbackCaptor.capture());
        assertNotNull(callbackCaptor.getValue());
        verify(digitsEventCollector).startFindMatches(any(ContactsLookupStartDetails.class));
    }

    @Test
    public void testGetContactMatches_countAboveMax() {
        final String cursor = "";
        final Integer count = 101;

        contactsClient.lookupContactMatches(cursor, count, callback);

        verify(sdkService).usersAndUploadedBy(eq(cursor), eq((Integer) null),
                callbackCaptor.capture());
        assertNotNull(callbackCaptor.getValue());
        verify(digitsEventCollector).startFindMatches(any(ContactsLookupStartDetails.class));
    }

    @Test
    public void testGetContactMatches_countNull() {
        final String cursor = "";

        contactsClient.lookupContactMatches(cursor, null, callback);

        verify(sdkService).usersAndUploadedBy(eq(cursor), eq((Integer) null),
                callbackCaptor.capture());
        assertNotNull(callbackCaptor.getValue());
        verify(digitsEventCollector).startFindMatches(any(ContactsLookupStartDetails.class));
    }

    @Test
    public void testUploadContacts() {
        final Vcards vCards = new Vcards(new ArrayList<String>());

        contactsClient.uploadContacts(vCards);

        verify(sdkService).upload(vCards);
    }

}
