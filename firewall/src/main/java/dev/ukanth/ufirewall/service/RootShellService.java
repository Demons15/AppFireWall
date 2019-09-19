package dev.ukanth.ufirewall.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.cxsz.framework.tool.LogUtil;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import eu.chainfire.libsuperuser.Shell;

import static dev.ukanth.ufirewall.service.RootShellService.ShellState.INIT;


public class RootShellService extends Service {

    public static final String TAG = "AFWall";
    public static final int EXIT_NO_ROOT_ACCESS = -1;
    public static final int NO_TOAST = -1;
    /* write command completion times to logcat */
    private static final boolean enableProfiling = false;
    //number of retries - increase the count
    private final static int MAX_RETRIES = 10;
    private static Shell.Interactive rootSession;
    private static Context mContext;
    private static ShellState rootState = INIT;
    private static LinkedList<RootCommand> waitQueue = new LinkedList<RootCommand>();

    private static void complete(final RootCommand state, int exitCode) {
        if (enableProfiling) {
            LogUtil.setTagI(TAG, "RootShell: " + state.getCommmands().size() + " commands completed in " +
                    (new Date().getTime() - state.startTime.getTime()) + " ms");
        }
        state.exitCode = exitCode;
        state.done = true;
        if (state.cb != null) {
            state.cb.cbFunc(state);
        }

        if (exitCode == 0 && state.successToast != NO_TOAST) {
            LogUtil.setTagI("配置结果:", mContext.getString(state.successToast));
        } else if (exitCode != 0 && state.failureToast != NO_TOAST) {
            LogUtil.setTagI("配置结果:", mContext.getString(state.failureToast));
        }
    }

    private static void runNextSubmission() {

        do {
            RootCommand state;
            try {
                state = waitQueue.remove();
            } catch (NoSuchElementException e) {
                // nothing left to do
                if (rootState == ShellState.BUSY) {
                    rootState = ShellState.READY;
                }
                break;
            }

            if (state != null) {
                LogUtil.setTagI(TAG, "Start processing next state");
                if (enableProfiling) {
                    state.startTime = new Date();
                }

                if (rootState == ShellState.FAIL) {
                    // if we don't have root, abort all queued commands
                    complete(state, EXIT_NO_ROOT_ACCESS);
                    continue;
                } else if (rootState == ShellState.READY) {
                    //LogUtil.setTagI(TAG, "Total commamds: #" + state.getCommmands().size());
                    rootState = ShellState.BUSY;
                    processCommands(state);
                }
            }


        } while (false);
    }

    private static void processCommands(final RootCommand state) {
        if (state.commandIndex < state.getCommmands().size() && state.getCommmands().get(state.commandIndex) != null) {
            String command = state.getCommmands().get(state.commandIndex);
            //not to send conflicting status
            if (!state.isv6) {
                sendUpdate(state);
            }
            if (command != null) {
                state.ignoreExitCode = false;

                if (command.startsWith("#NOCHK# ")) {
                    command = command.replaceFirst("#NOCHK# ", "");
                    state.ignoreExitCode = true;
                }
                state.lastCommand = command;
                state.lastCommandResult = new StringBuilder();
                try {
                    rootSession.addCommand(command, 0, (commandCode, exitCode, output) -> {
                        if (output != null) {
                            ListIterator<String> iter = output.listIterator();
                            while (iter.hasNext()) {
                                String line = iter.next();
                                if (line != null && !line.equals("")) {
                                    if (state.res != null) {
                                        state.res.append(line + "\n");
                                    }
                                    state.lastCommandResult.append(line + "\n");
                                }
                            }
                        }
                        if (exitCode >= 0 && state.retryCount < MAX_RETRIES) {
                            //lets wait for few ms before trying ?
                            state.retryCount++;
                            LogUtil.setTagI(TAG, "command '" + state.lastCommand + "' exited with status " + exitCode +
                                    ", retrying (attempt " + state.retryCount + "/" + MAX_RETRIES + ")");
                            processCommands(state);
                            return;
                        }

                        state.commandIndex++;
                        state.retryCount = 0;

                        boolean errorExit = exitCode != 0 && !state.ignoreExitCode;
                        if (state.commandIndex >= state.getCommmands().size() || errorExit) {
                            complete(state, exitCode);
                            if (exitCode < 0) {
                                rootState = ShellState.FAIL;
                                LogUtil.setTagE(TAG, "libsuperuser error " + exitCode + " on command '" + state.lastCommand + "'");
                            } else {
                                if (errorExit) {
                                    LogUtil.setTagI(TAG, "command '" + state.lastCommand + "' exited with status " + exitCode +
                                            "\nOutput:\n" + state.lastCommandResult);
                                }
                                rootState = ShellState.READY;
                            }
                            runNextSubmission();
                        } else {
                            processCommands(state);
                        }
                    });
                } catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
                    LogUtil.setTagE(TAG, e.getMessage());
                }
            }
        } else {
            complete(state, 0);
        }
    }

    private static void sendUpdate(final RootCommand state2) {
        new Thread(() -> {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("UPDATEUI");
            broadcastIntent.putExtra("SIZE", state2.getCommmands().size());
            broadcastIntent.putExtra("INDEX", state2.commandIndex);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcastIntent);
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    private void startShellInBackground() {
        LogUtil.setTagI(TAG, "Starting root shell...");
        //start only rootSession is null
        if (rootSession == null) {

            rootSession = new Shell.Builder().
                    useSH().
                    setWantSTDERR(true).
                    setWatchdogTimeout(5).
                    open((commandCode, exitCode, output) -> {
                        if (exitCode < 0) {
//                            LogUtil.setTagE(TAG, "Can't open root shell: exitCode " + exitCode);
//                            rootState = ShellState.FAIL;
                        } else {
//                            LogUtil.setTagI(TAG, "Root shell is open");
//                            rootState = ShellState.READY;
                        }
//                        runNextSubmission();
                    });
            rootState = ShellState.READY;
            runNextSubmission();
        }

    }

    private void reOpenShell(Context context) {
        if (rootState == null || rootState != ShellState.READY || rootState == ShellState.FAIL) {
            rootState = ShellState.BUSY;
            startShellInBackground();
            Intent intent = new Intent(context, RootShellService.class);
            context.startService(intent);
        }
    }


    public void runScriptAsRoot(Context ctx, List<String> cmds, RootCommand state) {
        LogUtil.setTagI(TAG, "Received cmds: #" + cmds.size());
        state.setCommmands(cmds);
        state.commandIndex = 0;
        state.retryCount = 0;
        if (mContext == null) {
            mContext = ctx.getApplicationContext();
        }
        waitQueue.add(state);
        if (rootState == INIT || (rootState == ShellState.FAIL && state.reopenShell)) {
            reOpenShell(ctx);
        } else if (rootState != ShellState.BUSY) {
            runNextSubmission();
        } else {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    LogUtil.setTagI(TAG, "State of rootShell: " + rootState);
                    if (rootState == ShellState.BUSY) {
                        //try resetting state to READY forcefully
                        LogUtil.setTagI(TAG, "Forcefully changing the state " + rootState);
                        rootState = ShellState.READY;
                    }
                    runNextSubmission();
                }
            }, 5000);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public enum ShellState {
        INIT,
        READY,
        BUSY,
        FAIL
    }
}