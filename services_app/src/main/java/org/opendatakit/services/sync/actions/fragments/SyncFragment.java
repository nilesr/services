/*
 * Copyright (C) 2015 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.services.sync.actions.fragments;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.fasterxml.jackson.core.type.TypeReference;
import org.opendatakit.consts.IntentConsts;
import org.opendatakit.database.RoleConsts;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.services.sync.actions.activities.*;
import org.opendatakit.services.utilities.ODKServicesPropertyUtils;
import org.opendatakit.sync.service.*;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.services.R;
import org.opendatakit.services.preferences.activities.IOdkAppPropertiesActivity;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.services.sync.actions.SyncActions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mitchellsundt@gmail.com
 */
public class SyncFragment extends Fragment implements ISyncOutcomeHandler {

  private static final String TAG = "SyncFragment";

  public static final String NAME = "SyncFragment";
  public static final int ID = R.layout.sync_launch_fragment;

  private static final String ACCOUNT_TYPE_G = "com.google";

  private static final String SYNC_ATTACHMENT_TREATMENT = "syncAttachmentState";

  private static final String SYNC_ACTION = "syncAction";

  private static final String PROGRESS_DIALOG_TAG = "progressDialog";

  private static final String OUTCOME_DIALOG_TAG = "outcomeDialog";

  private String mAppName;

  private final Handler handler = new Handler();
  private DismissableProgressDialogFragment progressDialog = null;
  private DismissableOutcomeDialogFragment outcomeDialog = null;

  private boolean loggingIn = false;

  private LinearLayout infoPane;
  private TextView uriField;
  private TextView accountAuthType;
  private TextView accountIdentity;

  private Spinner syncInstanceAttachmentsSpinner;

  private Button startSync;
  private Button resetServer;
  private Button changeUser;

  private LinearLayout resetButtonPane;

  private SyncAttachmentState syncAttachmentState = SyncAttachmentState.SYNC;
  private SyncActions syncAction = SyncActions.IDLE;

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(SYNC_ATTACHMENT_TREATMENT, syncAttachmentState.name());
    outState.putString(SYNC_ACTION, syncAction.name());
  }


  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    Intent incomingIntent = getActivity().getIntent();
    mAppName = incomingIntent.getStringExtra(IntentConsts.INTENT_KEY_APP_NAME);
    if ( mAppName == null || mAppName.length() == 0 ) {
      getActivity().setResult(Activity.RESULT_CANCELED);
      getActivity().finish();
      return;
    }

    if ( savedInstanceState != null && savedInstanceState.containsKey(SYNC_ATTACHMENT_TREATMENT) ) {
      String treatment = savedInstanceState.getString(SYNC_ATTACHMENT_TREATMENT);
      try {
        syncAttachmentState = SyncAttachmentState.valueOf(treatment);
      } catch ( IllegalArgumentException e ) {
        syncAttachmentState = SyncAttachmentState.SYNC;
      }
    }

    if ( savedInstanceState != null && savedInstanceState.containsKey(SYNC_ACTION) ) {
      String action = savedInstanceState.getString(SYNC_ACTION);
      try {
        syncAction = SyncActions.valueOf(action);
      } catch ( IllegalArgumentException e ) {
        syncAction = SyncActions.IDLE;
      }
    }
    disableButtons();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);

    View view = inflater.inflate(ID, container, false);

    infoPane = (LinearLayout) view.findViewById(R.id.sync_info_pane);
    uriField = (TextView) view.findViewById(R.id.sync_uri_field);
    accountAuthType = (TextView) view.findViewById(R.id.sync_account_auth_label);
    accountIdentity = (TextView) view.findViewById(R.id.sync_account);

    syncInstanceAttachmentsSpinner = (Spinner) view.findViewById(R.id.sync_instance_attachments);

    if ( savedInstanceState != null && savedInstanceState.containsKey(SYNC_ATTACHMENT_TREATMENT) ) {
      String treatment = savedInstanceState.getString(SYNC_ATTACHMENT_TREATMENT);
      try {
        syncAttachmentState = SyncAttachmentState.valueOf(treatment);
      } catch ( IllegalArgumentException e ) {
        syncAttachmentState = SyncAttachmentState.SYNC;
      }
    }

    if ( savedInstanceState != null && savedInstanceState.containsKey(SYNC_ACTION) ) {
      String action = savedInstanceState.getString(SYNC_ACTION);
      try {
        syncAction = SyncActions.valueOf(action);
      } catch ( IllegalArgumentException e ) {
        syncAction = SyncActions.IDLE;
      }
    }

    ArrayAdapter<CharSequence> instanceAttachmentsAdapter = ArrayAdapter.createFromResource(
        getActivity(), R.array.sync_attachment_option_names, android.R.layout.select_dialog_item);
    syncInstanceAttachmentsSpinner.setAdapter(instanceAttachmentsAdapter);

    syncInstanceAttachmentsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(AdapterView<?> parent, View view, int position,
          long id) {
        String[] syncAttachmentType =
            getResources().getStringArray(R.array.sync_attachment_option_values);
        syncAttachmentState = SyncAttachmentState.valueOf(syncAttachmentType[position]);
      }

      @Override public void onNothingSelected(AdapterView<?> parent) {
        String[] syncAttachmentType =
            getResources().getStringArray(R.array.sync_attachment_option_values);
        syncAttachmentState = SyncAttachmentState.SYNC;
        for ( int i = 0 ; i < syncAttachmentType.length ; ++i ) {
          if ( syncAttachmentType[i].equals(syncAttachmentState.name()) ) {
            syncInstanceAttachmentsSpinner.setSelection(i);
            break;
          }
        }
      }
    });

    startSync = (Button) view.findViewById(R.id.sync_start_button);
    startSync.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        onClickSyncNow(v);
      }
    });
    resetServer = (Button) view.findViewById(R.id.sync_reset_server_button);
    resetServer.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        onClickResetServer(v);
      }
    });
    changeUser = (Button) view.findViewById(R.id.change_user_button);
    changeUser.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        onClickChangeUser(v);
      }
    });

    resetButtonPane = (LinearLayout) view.findViewById(R.id.sync_reset_button_pane);

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();

    WebLogger.getLogger(getAppName()).i(TAG, "[" + getId() + "] [onResume]");

    Intent incomingIntent = getActivity().getIntent();
    mAppName = incomingIntent.getStringExtra(IntentConsts.INTENT_KEY_APP_NAME);
    if ( mAppName == null || mAppName.length() == 0 ) {
      WebLogger.getLogger(getAppName()).i(TAG, "[" + getId() + "] [onResume] mAppName is null so calling finish");
      getActivity().setResult(Activity.RESULT_CANCELED);
      getActivity().finish();
      return;
    }

    updateCredentialsUI();
    perhapsEnableButtons();
    updateInterface();
  }

  private void updateCredentialsUI() {
    PropertiesSingleton props = ((IOdkAppPropertiesActivity) this.getActivity()).getProps();
    uriField.setText(props.getProperty(CommonToolProperties.KEY_SYNC_SERVER_URL));

    String credentialToUse = props.getProperty(CommonToolProperties.KEY_AUTHENTICATION_TYPE);
    String[] credentialValues = getResources().getStringArray(R.array.credential_entry_values);
    String[] credentialEntries = getResources().getStringArray(R.array.credential_entries);

    if ( credentialToUse == null ) {
      credentialToUse = getString(R.string.credential_type_none);
    }

    for ( int i = 0 ; i < credentialValues.length ; ++i ) {
      if ( credentialToUse.equals(credentialValues[i]) ) {
        if (!credentialToUse.equals(getString(R.string.credential_type_none))) {
          accountAuthType.setText(credentialEntries[i]);
        }
      }
    }

    String account = ODKServicesPropertyUtils.getActiveUser(props);
    int indexOfColon = account.indexOf(':');
    if (indexOfColon > 0) {
      account = account.substring(indexOfColon + 1);
    }
    if ( credentialToUse.equals(getString(R.string.credential_type_none))) {
      accountIdentity.setText(getResources().getString(R.string.anonymous));
    } else if ( credentialToUse.equals(getString(R.string.credential_type_username_password))) {
      accountIdentity.setText(account);
    } else if ( credentialToUse.equals(getString(R.string.credential_type_google_account))) {
      accountIdentity.setText(account);
    } else {
      accountIdentity.setText(getResources().getString(R.string.no_account));
    }

    String[] syncAttachmentValues =
        getResources().getStringArray(R.array.sync_attachment_option_values);
    for ( int i = 0 ; i < syncAttachmentValues.length ; ++i ) {
      if ( syncAttachmentState.name().equals(syncAttachmentValues[i]) ) {
        syncInstanceAttachmentsSpinner.setSelection(i);
        break;
      }
    }
  }

  private void disableButtons() {
    startSync.setEnabled(false);
    resetServer.setEnabled(false);
  }

  private void perhapsEnableButtons() {
    PropertiesSingleton props = ((IOdkAppPropertiesActivity) this.getActivity()).getProps();
    boolean isTablesAdmin;
    {
      String rolesList = props.getProperty(CommonToolProperties.KEY_ROLES_LIST);
      // figure out whether we have a privileged user or not
      ArrayList<String> rolesArray = null;
      if ( rolesList != null && rolesList.length() != 0 ) {
        try {
          TypeReference<ArrayList<String>> arrayListTypeReference;
          arrayListTypeReference = new TypeReference<ArrayList<String>>() {};
          rolesArray = ODKFileUtils.mapper.readValue(rolesList, arrayListTypeReference);
        } catch (IOException e) {
          throw new IllegalStateException("this should never happen");
        }
      }
      isTablesAdmin = (rolesArray != null) && rolesArray.contains(RoleConsts.ROLE_ADMINISTRATOR);
    }

    String url = props.getProperty(CommonToolProperties.KEY_SYNC_SERVER_URL);
    if ( url == null || url.length() == 0 ) {
      disableButtons();
    } else {
      startSync.setEnabled(true);
      resetServer.setEnabled(isTablesAdmin);
    }

    // only show information screens if we are the tables admin
    int visibility = isTablesAdmin ? View.VISIBLE : View.GONE;
    infoPane.setVisibility(View.VISIBLE); // TODO
    resetButtonPane.setVisibility(visibility);
  }

  AlertDialog.Builder buildOkMessage(String title, String message) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setCancelable(false);
    builder.setPositiveButton(getString(R.string.ok), null);
    builder.setTitle(title);
    builder.setMessage(message);
    return builder;
  }

  /**
   * Invoke this at the start of sync or reset
   */
  public void prepareForSyncAction() {
    // remove any settings for a URL other than the server URL...

    PropertiesSingleton props = ((IOdkAppPropertiesActivity) this.getActivity()).getProps();

    String authType = props.getProperty(CommonToolProperties.KEY_AUTHENTICATION_TYPE);
    if ( authType == null ) {
      authType = getString(R.string.credential_type_none);
    }

    if ( getString(R.string.credential_type_google_account).equals(authType)) {
      authenticateGoogleAccount();
    } else {
      tickleInterface();
    }
  }

  /**
   * Hooked up to authorizeAccountButton's onClick in aggregate_activity.xml
   */
  public void authenticateGoogleAccount() {
    WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [authenticateGoogleAccount] invalidated authtoken");
    invalidateAuthToken(getActivity(), getAppName());

    PropertiesSingleton props = CommonToolProperties.get(getActivity(), getAppName());
    Intent i = new Intent(getActivity(), AccountInfoActivity.class);
    Account account = new Account(props.getProperty(CommonToolProperties.KEY_ACCOUNT), ACCOUNT_TYPE_G);
    i.putExtra(IntentConsts.INTENT_KEY_APP_NAME, getAppName());
    i.putExtra(AccountInfoActivity.INTENT_EXTRAS_ACCOUNT, account);
    startActivityForResult(i, SyncActivity.AUTHORIZE_ACCOUNT_RESULT_CODE);
  }

  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if ( requestCode == SyncActivity.AUTHORIZE_ACCOUNT_RESULT_CODE ) {
      if ( resultCode == Activity.RESULT_CANCELED ) {
        invalidateAuthToken(getActivity(), getAppName());
        syncAction = SyncActions.IDLE;
      }
      tickleInterface();
    }
  }

  private void tickleInterface() {
    WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [tickleInterface] started");
    Activity activity = getActivity();
    if ( activity == null ) {
      // we are in transition -- do nothing
      WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [tickleInterface] activity == null");
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          tickleInterface();
        }
      }, 100);

      return;
    }
    ((ISyncServiceInterfaceActivity)activity)
        .invokeSyncInterfaceAction(new DoSyncActionCallback() {
      @Override public void doAction(OdkSyncServiceInterface syncServiceInterface)
          throws RemoteException {
        if ( syncServiceInterface != null ) {
//          WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [tickleInterface] syncServiceInterface != null");
          final SyncStatus status = syncServiceInterface.getSyncStatus(getAppName());
          final SyncProgressEvent event = syncServiceInterface.getSyncProgressEvent(getAppName());
          if (status == SyncStatus.SYNCING) {
            syncAction = SyncActions.MONITOR_SYNCING;

            handler.post(new Runnable() {
              @Override
              public void run() {
                showProgressDialog(status, event.progressState,
                    event.progressMessageText, event.curProgressBar, event.maxProgressBar);
              }
            });
            return;
          }

          switch (syncAction) {
          case SYNC:
            syncServiceInterface.synchronizeWithServer(getAppName(), syncAttachmentState);
            syncAction = SyncActions.MONITOR_SYNCING;

            handler.post(new Runnable() {
              @Override
              public void run() {
                showProgressDialog(SyncStatus.NONE, null, getString(R.string.sync_starting), -1, 0);
              }
            });
            break;
          case RESET_SERVER:
            syncServiceInterface.resetServer(getAppName(), syncAttachmentState);
            syncAction = SyncActions.MONITOR_SYNCING;

            handler.post(new Runnable() {
              @Override
              public void run() {
                showProgressDialog(SyncStatus.NONE, null, getString(R.string.sync_starting), -1, 0);
              }
            });
            break;
          case IDLE:
            if ( event.progressState == SyncProgressState.FINISHED ) {
              final SyncOverallResult result = syncServiceInterface.getSyncResult(getAppName());
              handler.post(new Runnable() {
                @Override
                public void run() {
                  showOutcomeDialog(status, result);
                }
              });
            }
          default:
            break;
          }
        } else {
          WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [tickleInterface] syncServiceInterface == null");
          // The service is not bound yet so now we need to try again
          handler.postDelayed(new Runnable() {
            @Override
            public void run() {
              tickleInterface();
            }
          }, 100);
        }
      }
    });
  }

  private void updateInterface() {
    Activity activity = getActivity();
    if ( activity == null ) {
      // we are in transition -- do nothing
      WebLogger.getLogger(getAppName())
          .w(TAG, "[" + getId() + "] [updateInterface] activity == null = return");
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          updateInterface();
        }
      }, 100);
      return;
    }
    ((ISyncServiceInterfaceActivity)activity)
        .invokeSyncInterfaceAction(new DoSyncActionCallback() {
          @Override
          public void doAction(OdkSyncServiceInterface syncServiceInterface)
              throws RemoteException {
            if ( syncServiceInterface != null ) {
              final SyncStatus status = syncServiceInterface.getSyncStatus(getAppName());
              final SyncProgressEvent event = syncServiceInterface.getSyncProgressEvent(getAppName());
              if (status == SyncStatus.SYNCING) {
                syncAction = SyncActions.MONITOR_SYNCING;

                handler.post(new Runnable() {
                  @Override
                  public void run() {
                    showProgressDialog(status, event.progressState, event.progressMessageText,
                        event.curProgressBar, event.maxProgressBar);
                  }
                });
                return;
              } else {
                // request completed
                syncAction = SyncActions.IDLE;
                final SyncOverallResult result = syncServiceInterface.getSyncResult(getAppName());
                handler.post(new Runnable() {
                  @Override
                  public void run() {
                    dismissProgressDialog();
                    if ( event.progressState == SyncProgressState.FINISHED ) {
                      showOutcomeDialog(status, result);
                    }
                  }
                });
                return;
              }
            } else {
              // The service is not bound yet so now we need to try again
              handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                  updateInterface();
                }
              }, 100);
            }
          }
        });
  }

  @Override
  public void onSyncCompleted() {
    Activity activity = getActivity();
    WebLogger.getLogger(getAppName()).i(TAG, "[" + getId() + "] [onSyncCompleted] after getActivity");
    if ( activity == null) {
      // we are in transition -- do nothing
      WebLogger.getLogger(getAppName()).i(TAG, "[" + getId() + "] [onSyncCompleted] activity == null = return");
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          onSyncCompleted();
        }
      }, 100);
      return;
    }

    ((ISyncServiceInterfaceActivity)activity)
            .invokeSyncInterfaceAction(new DoSyncActionCallback() {
              @Override
              public void doAction(OdkSyncServiceInterface syncServiceInterface)
                      throws RemoteException {
                WebLogger.getLogger(getAppName()).i(TAG, "[" + getId() + "] [onSyncCompleted] called");
                if ( syncServiceInterface != null ) {
                  WebLogger.getLogger(getAppName()).i(TAG, "[" + getId() + "] [onSyncCompleted] and syncServiceInterface is not null");
                  boolean completed = syncServiceInterface.clearAppSynchronizer(getAppName());
                  if (!completed) {
                    throw new IllegalStateException("Could not remove AppSynchronizer for " + getAppName());
                  }
                  return;
                } else {
                  WebLogger.getLogger(getAppName()).i(TAG, "[" + getId() + "] [onSyncCompleted] and syncServiceInterface is null");
                  handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                      onSyncCompleted();
                    }
                  }, 100);
                }
              }
            });
  }

  public boolean areCredentialsConfigured() {
    // verify that we have the necessary credentials
    PropertiesSingleton props = CommonToolProperties.get(getActivity(), getAppName());
    String authType = props.getProperty(CommonToolProperties.KEY_AUTHENTICATION_TYPE);
    if ( getString(R.string.credential_type_none).equals(authType) ) {
      return true;
    }
    if ( getString(R.string.credential_type_username_password).equals(authType) ) {
      String username = props.getProperty(CommonToolProperties.KEY_USERNAME);
      String password = props.getProperty(CommonToolProperties.KEY_PASSWORD);
      if ( username == null || username.length() == 0 ||
          password == null || password.length() == 0 ) {
        Toast.makeText(getActivity(), getString(R.string.sync_configure_username_password),
            Toast.LENGTH_LONG).show();
        return false;
      }
      return true;
    }
    if ( getString(R.string.credential_type_google_account).equals(authType) ) {
      String accountName = props.getProperty(CommonToolProperties.KEY_ACCOUNT);
      if (accountName == null || accountName.length() == 0) {
        Toast.makeText(getActivity(), getString(R.string.sync_configure_google_account),
            Toast.LENGTH_LONG).show();
        return false;
      }
      return true;
    }
    Toast.makeText(getActivity(), getString(R.string.sync_configure_credentials),
        Toast.LENGTH_LONG).show();
    return false;
  }

  /**
   * Hooked to sync_reset_server_button's onClick in sync_launch_fragment.xml
   */
  public void onClickResetServer(View v) {
    WebLogger.getLogger(getAppName()).d(TAG, "[" + getId() + "] [onClickResetServer]");
    // ask whether to sync app files and table-level files

    if (areCredentialsConfigured()) {
      // show warning message
      AlertDialog.Builder msg = buildOkMessage(getString(R.string.sync_confirm_reset_app_server),
          getString(R.string.sync_reset_app_server_warning));

      msg.setPositiveButton(getString(R.string.sync_reset), new DialogInterface.OnClickListener() {
        @Override public void onClick(DialogInterface dialog, int which) {
          WebLogger.getLogger(getAppName()).d(TAG,
              "[" + getId() + "] [onClickResetServer] timestamp: " + System.currentTimeMillis());
          disableButtons();
          syncAction = SyncActions.RESET_SERVER;
          prepareForSyncAction();
        }
      });

      msg.setNegativeButton(getString(R.string.cancel), null);
      msg.show();
    }
  }

  /**
   * Hooked to syncNowButton's onClick in aggregate_activity.xml
   */
  public void onClickSyncNow(View v) {
    WebLogger.getLogger(getAppName()).d(TAG,
        "[" + getId() + "] [onClickSyncNow] timestamp: " + System.currentTimeMillis());
    if (areCredentialsConfigured()) {
      disableButtons();
      syncAction = SyncActions.SYNC;
      prepareForSyncAction();
    }
  }

  public void onClickChangeUser(View v) {
    WebLogger.getLogger(getAppName()).d(TAG,
        "[" + getId() + "] [onClickChangeUser] timestamp: " + System.currentTimeMillis());

     Intent i = new Intent(getActivity(), LoginActivity.class);
     i.putExtra(IntentConsts.INTENT_KEY_APP_NAME, mAppName);
     startActivity(i);
     return;
  }

  public static void invalidateAuthToken(Context context, String appName) {
    PropertiesSingleton props = CommonToolProperties.get(context, appName);
    AccountManager.get(context).invalidateAuthToken(ACCOUNT_TYPE_G,
        props.getProperty(CommonToolProperties.KEY_AUTH));
    Map<String,String> properties = new HashMap<String,String>();
    properties.put(CommonToolProperties.KEY_AUTH, null);
    properties.put(CommonToolProperties.KEY_ROLES_LIST, "");
    properties.put(CommonToolProperties.KEY_DEFAULT_GROUP, "");
    properties.put(CommonToolProperties.KEY_USERS_LIST, "");
    props.setProperties(properties);
  }


  @Override public void onDestroy() {
    super.onDestroy();
    handler.removeCallbacksAndMessages(null);
    WebLogger.getLogger(getAppName()).i(TAG, "[" + getId() + "] [onDestroy]");
  }

  private void showProgressDialog( SyncStatus status, SyncProgressState progress, String message,
      int progressStep, int maxStep ) {
    if ( getActivity() == null ) {
      // we are tearing down or still initializing
      return;
    }
    if ( syncAction == SyncActions.MONITOR_SYNCING ) {

      disableButtons();

      if ( progress == null ) {
        progress = SyncProgressState.INACTIVE;
      }

      int id_title;
      switch ( progress ) {
      case APP_FILES:
        id_title = R.string.sync_app_level_files;
        break;
      case TABLE_FILES:
        id_title = R.string.sync_table_level_files;
        break;
      case ROWS:
        id_title = R.string.sync_row_data;
        break;
      default:
        id_title = R.string.sync_in_progress;
      }

      // try to retrieve the active dialog
      Fragment dialog = getFragmentManager().findFragmentByTag(PROGRESS_DIALOG_TAG);

      if (dialog != null && ((DismissableProgressDialogFragment) dialog).getDialog() != null) {
        ((DismissableProgressDialogFragment) dialog).getDialog().setTitle(id_title);
        ((DismissableProgressDialogFragment) dialog).setMessage(message, progressStep, maxStep);
      } else if (progressDialog != null && progressDialog.getDialog() != null) {
        progressDialog.getDialog().setTitle(id_title);
        progressDialog.setMessage(message, progressStep, maxStep);
      } else {
        if (progressDialog != null) {
          dismissProgressDialog();
        }
        progressDialog = DismissableProgressDialogFragment.newInstance(getString(id_title), message);

        // If fragment is not visible an exception could be thrown
        // TODO: Investigate a better way to handle this
        try {
          progressDialog.show(getFragmentManager(), PROGRESS_DIALOG_TAG);
        } catch (IllegalStateException ise) {
          ise.printStackTrace();
        }
      }
      if ( status == SyncStatus.SYNCING || status == SyncStatus.NONE) {
        handler.postDelayed(new Runnable() {
          @Override public void run() {
            updateInterface();
          }
        }, 150);
      }
    }
  }

  private void dismissProgressDialog() {
    if ( getActivity() == null ) {
      // we are tearing down or still initializing
      return;
    }

    // try to retrieve the active dialog
    final Fragment dialog = getFragmentManager().findFragmentByTag(PROGRESS_DIALOG_TAG);

    if (dialog != null && dialog != progressDialog) {
      // the UI may not yet have resolved the showing of the dialog.
      // use a handler to add the dismiss to the end of the queue.
      handler.post(new Runnable() {
        @Override
        public void run() {
          try {
            ((DismissableProgressDialogFragment) dialog).dismiss();
          } catch (Exception e) {
            // ignore... we tried!
          }
          perhapsEnableButtons();
        }
      });
    }
    if (progressDialog != null) {
      final DismissableProgressDialogFragment scopedReference = progressDialog;
      progressDialog = null;
      // the UI may not yet have resolved the showing of the dialog.
      // use a handler to add the dismiss to the end of the queue.
      handler.post(new Runnable() {
        @Override public void run() {
          try {
            scopedReference.dismiss();
          } catch (Exception e) {
            // ignore... we tried!
          }
          perhapsEnableButtons();
        }
      });
    }
  }

  private void showOutcomeDialog( SyncStatus status, SyncOverallResult result ) {
    if ( getActivity() == null ) {
      // we are tearing down or still initializing
      return;
    }
    if ( syncAction == SyncActions.IDLE ) {

      disableButtons();

      String message;
      int id_title;
      switch ( status ) {
        default:
          throw new IllegalStateException("Unexpected missing case statement");
        case
                /** earlier sync ended with socket or lower level transport or protocol error (e.g., 300's) */ NETWORK_TRANSPORT_ERROR:
          id_title = R.string.sync_communications_error;
          message = getString(R.string.sync_status_network_transport_error);
          break;
        case
                /** earlier sync ended with Authorization denied (authentication and/or access) error */ AUTHENTICATION_ERROR:
          id_title = R.string.sync_user_authorization_failure;
          message = getString(R.string.sync_status_authentication_error);
          break;
        case
                /** earlier sync ended with a 500 error from server */ SERVER_INTERNAL_ERROR:
          id_title = R.string.sync_communications_error;
          message = getString(R.string.sync_status_internal_server_error);
          break;
        case /** the server is not an ODK Server - bad client config */ SERVER_IS_NOT_ODK_SERVER:
          id_title = R.string.sync_device_configuration_failure;
          message = getString(R.string.sync_status_bad_gateway_or_client_config);
          break;
        case
                /** earlier sync ended with a 400 error that wasn't Authorization denied */ REQUEST_OR_PROTOCOL_ERROR:
          id_title = R.string.sync_communications_error;
          message = getString(R.string.sync_status_request_or_protocol_error);
          break;
        case /** no earlier sync and no active sync */ NONE:
        case /** active sync -- get SyncProgressEvent to see current status */ SYNCING:
        case
                /** error accessing or updating database */ DEVICE_ERROR:
          id_title = R.string.sync_device_internal_error;
          message = getString(R.string.sync_status_device_internal_error);
          break;
        case
                /** the server is not configured for this appName -- Site Admin / Preferences */ APPNAME_NOT_SUPPORTED_BY_SERVER:
          id_title = R.string.sync_server_configuration_failure;
          message = getString(R.string.sync_status_appname_not_supported_by_server);
          break;
        case
                /** the server does not have any configuration, or no configuration for this client version */ SERVER_MISSING_CONFIG_FILES:
          id_title = R.string.sync_server_configuration_failure;
          message = getString(R.string.sync_status_server_missing_config_files);
          break;
        case
                /** the device does not have any configuration to push to server */ SERVER_RESET_FAILED_DEVICE_HAS_NO_CONFIG_FILES:
          id_title = R.string.sync_device_configuration_failure;
          message = getString(R.string.sync_status_server_reset_failed_device_has_no_config_files);
          break;
        case
                /** while a sync was in progress, another device reset the app config, requiring a restart of
                 * our sync */ RESYNC_BECAUSE_CONFIG_HAS_BEEN_RESET_ERROR:
          id_title = R.string.sync_resync_because_config_reset_error;
          message = getString(R.string.sync_status_resync_because_config_has_been_reset_error);
          break;
        case
                /** earlier sync ended with one or more tables containing row conflicts or checkpoint rows */ CONFLICT_RESOLUTION:
          id_title = R.string.sync_conflicts_need_resolving;
          message = getString(R.string.sync_conflicts_text);
          break;
        case
                /** earlier sync ended successfully without conflicts and all row-level attachments sync'd */ SYNC_COMPLETE:
          id_title = R.string.sync_successful;
          message = getString(R.string.sync_successful_text);
          break;
        case
                /** earlier sync ended successfully without conflicts but needs row-level attachments sync'd */ SYNC_COMPLETE_PENDING_ATTACHMENTS:
          id_title = R.string.sync_complete_pending_attachments;
          message = getString(R.string.sync_complete_pending_attachments_text);
      }
      // try to retrieve the active dialog
      Fragment dialog = getFragmentManager().findFragmentByTag(OUTCOME_DIALOG_TAG);

      if (dialog != null && ((DismissableOutcomeDialogFragment) dialog).getDialog() != null) {
        ((DismissableOutcomeDialogFragment) dialog).getDialog().setTitle(id_title);
        ((DismissableOutcomeDialogFragment) dialog).setMessage(message);
      } else if (outcomeDialog != null && outcomeDialog.getDialog() != null) {
        outcomeDialog.getDialog().setTitle(id_title);
        outcomeDialog.setMessage(message);
      } else {
        if (outcomeDialog != null) {
          dismissOutcomeDialog();
        }
        outcomeDialog = DismissableOutcomeDialogFragment.newInstance(getString(id_title),
            message,
            (status == SyncStatus.SYNC_COMPLETE ||
             status == SyncStatus.SYNC_COMPLETE_PENDING_ATTACHMENTS), SyncFragment.NAME);

        // If fragment is not visible an exception could be thrown
        // TODO: Investigate a better way to handle this
        try {
          outcomeDialog.show(getFragmentManager(), OUTCOME_DIALOG_TAG);
        } catch (IllegalStateException ise) {
          ise.printStackTrace();
        }
      }
    }
  }

  private void dismissOutcomeDialog() {
    if ( getActivity() == null ) {
      // we are tearing down or still initializing
      return;
    }

    // try to retrieve the active dialog
    final Fragment dialog = getFragmentManager().findFragmentByTag(PROGRESS_DIALOG_TAG);

    if (dialog != null && dialog != outcomeDialog) {
      // the UI may not yet have resolved the showing of the dialog.
      // use a handler to add the dismiss to the end of the queue.
      handler.post(new Runnable() {
        @Override
        public void run() {
          try {
            ((DismissableOutcomeDialogFragment) dialog).dismiss();
          } catch (Exception e) {
            // ignore... we tried!
          }
          perhapsEnableButtons();
        }
      });
    }
    if (outcomeDialog != null) {
      final DismissableOutcomeDialogFragment scopedReference = outcomeDialog;
      outcomeDialog = null;
      // the UI may not yet have resolved the showing of the dialog.
      // use a handler to add the dismiss to the end of the queue.
      handler.post(new Runnable() {
        @Override public void run() {
          try {
            scopedReference.dismiss();
          } catch (Exception e) {
            // ignore... we tried!
          }
          perhapsEnableButtons();
        }
      });
    }
  }

  public String getAppName() {
    if ( mAppName == null ) {
      throw new IllegalStateException("appName not yet initialized");
    }
    return mAppName;
  }

}
