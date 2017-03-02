/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.morlunk.mumbleclient.channel;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.IJumbleSession;
import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IMessage;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.IJumbleObserver;
import com.morlunk.jumble.util.JumbleDisconnectedException;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.service.IChatMessage;
import com.morlunk.mumbleclient.util.JumbleServiceFragment;
import com.morlunk.mumbleclient.util.MumbleImageGetter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChannelChatFragment extends JumbleServiceFragment implements ChatTargetProvider.OnChatTargetSelectedListener {
    private static final Pattern LINK_PATTERN = Pattern.compile("(https?://\\S+)");

	private IJumbleObserver mServiceObserver = new JumbleObserver() {

        @Override
        public void onMessageLogged(IMessage message) {
            addChatMessage(new IChatMessage.TextMessage(message), true);
        }

        @Override
        public void onLogInfo(String message) {
            addChatMessage(new IChatMessage.InfoMessage(IChatMessage.InfoMessage.Type.INFO, message), true);
        }

        @Override
        public void onLogWarning(String message) {
            addChatMessage(new IChatMessage.InfoMessage(IChatMessage.InfoMessage.Type.WARNING, message), true);
        }

        @Override
        public void onLogError(String message) {
            addChatMessage(new IChatMessage.InfoMessage(IChatMessage.InfoMessage.Type.ERROR, message), true);
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            IJumbleService service = getService();
            if (service.isConnected()) {
                IJumbleSession session = service.getSession();
                if (user != null && session.getSessionUser() != null &&
                        user.equals(session.getSessionUser()) &&
                        mTargetProvider.getChatTarget() == null) {
                    // Update chat target when user changes channels without a target.
                    updateChatTargetText(null);
                }
            }
        }
    };

    private ListView mChatList;
    private ChannelChatAdapter mChatAdapter;
	private EditText mChatTextEdit;
	private ImageButton mSendButton;
    private ChatTargetProvider mTargetProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mTargetProvider = (ChatTargetProvider) getParentFragment();
        } catch (ClassCastException e) {
            throw new ClassCastException(getParentFragment().toString()+" must implement ChatTargetProvider");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mTargetProvider.registerChatTargetListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mTargetProvider.unregisterChatTargetListener(this);
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_chat, container, false);
        mChatList = (ListView) view.findViewById(R.id.chat_list);
		mChatTextEdit = (EditText) view.findViewById(R.id.chatTextEdit);
		
		mSendButton = (ImageButton) view.findViewById(R.id.chatTextSend);
		mSendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
		
		mChatTextEdit.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                sendMessage();
                return true;
            }
        });
		
		mChatTextEdit.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mSendButton.setEnabled(mChatTextEdit.getText().length() > 0);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        updateChatTargetText(mTargetProvider.getChatTarget());
        return view;
	}

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_clear_chat:
                clear();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Adds the passed text to the fragment chat body.
     * @param message The message to add.
     * @param scroll Whether to scroll to the bottom after adding the message.
     */
    public void addChatMessage(IChatMessage message, boolean scroll) {
		if(mChatAdapter == null) return;

        mChatAdapter.add(message);

        if(scroll) {
            mChatList.post(new Runnable() {

                @Override
                public void run() {
                    mChatList.smoothScrollToPosition(mChatAdapter.getCount() - 1);
                }
            });
        }
	}

    /**
     * Sends the message currently in {@link com.morlunk.mumbleclient.channel.ChannelChatFragment#mChatTextEdit}
     * to the remote server. Clears the message box if the message was sent successfully.
     * @throws JumbleDisconnectedException If the service is disconnected.
     */
	private void sendMessage() throws JumbleDisconnectedException {
        if(mChatTextEdit.length() == 0) return;
        String message = mChatTextEdit.getText().toString();
        String formattedMessage = markupOutgoingMessage(message);
        ChatTargetProvider.ChatTarget target = mTargetProvider.getChatTarget();
        IMessage responseMessage = null;
        IJumbleSession session = getService().getSession();
        if(target == null)
            responseMessage = session.sendChannelTextMessage(session.getSessionChannel().getId(), formattedMessage, false);
        else if(target.getUser() != null)
            responseMessage = session.sendUserTextMessage(target.getUser().getSession(), formattedMessage);
        else if(target.getChannel() != null)
            responseMessage = session.sendChannelTextMessage(target.getChannel().getId(), formattedMessage, false);
        addChatMessage(new IChatMessage.TextMessage(responseMessage), true);
        mChatTextEdit.setText("");
	}

    /**
     * Adds HTML markup to the message, replacing links and newlines.
     * @param message The message to markup.
     * @return HTML data.
     */
    private String markupOutgoingMessage(String message) {
        String formattedBody = message;
        Matcher matcher = LINK_PATTERN.matcher(formattedBody);
        formattedBody = matcher.replaceAll("<a href=\"$1\">$1</a>")
                .replaceAll("\n", "<br>");
        return formattedBody;
    }
	
	public void clear() {
        mChatAdapter.clear();
        getService().clearMessageLog();
    }

	/**
	 * Updates hint displaying chat target.
	 */
	public void updateChatTargetText(final ChatTargetProvider.ChatTarget target) {
        if(getService() == null || !getService().isConnected()) return;

        IJumbleSession session = getService().getSession();
        String hint = null;
        if(target == null && session.getSessionChannel() != null) {
            hint = getString(R.string.messageToChannel, session.getSessionChannel().getName());
        } else if(target != null && target.getUser() != null) {
            hint = getString(R.string.messageToUser, target.getUser().getName());
        } else if(target != null && target.getChannel() != null) {
            hint = getString(R.string.messageToChannel, target.getChannel().getName());
        }
        mChatTextEdit.setHint(hint);
        mChatTextEdit.requestLayout(); // Needed to update bounds after hint change.
	}


    @Override
    public void onServiceBound(IJumbleService service) {
        mChatAdapter = new ChannelChatAdapter(getActivity(), service, getService().getMessageLog());
        mChatList.setAdapter(mChatAdapter);
        mChatList.post(new Runnable() {
            @Override
            public void run() {
                mChatList.setSelection(mChatAdapter.getCount() - 1);
            }
        });
    }

    @Override
    public IJumbleObserver getServiceObserver() {
        return mServiceObserver;
    }

    @Override
    public void onChatTargetSelected(ChatTargetProvider.ChatTarget target) {
        updateChatTargetText(target);
    }

    private static class ChannelChatAdapter extends ArrayAdapter<IChatMessage> {
        private final MumbleImageGetter mImageGetter;
        private final IJumbleService mService;
        private final DateFormat mDateFormat;

        public ChannelChatAdapter(Context context, IJumbleService service, List<IChatMessage> messages) {
            super(context, 0, new ArrayList<>(messages));
            mService = service;
            mImageGetter = new MumbleImageGetter(context);
            mDateFormat = SimpleDateFormat.getTimeInstance();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if(v == null) {
                v = LayoutInflater.from(getContext()).inflate(R.layout.list_chat_item, parent, false);
            }

            final LinearLayout chatBox = (LinearLayout) v.findViewById(R.id.list_chat_item_box);
            final TextView targetText = (TextView) v.findViewById(R.id.list_chat_item_target);
            final TextView messageText = (TextView) v.findViewById(R.id.list_chat_item_text);
            TextView timeText = (TextView) v.findViewById(R.id.list_chat_item_time);

            IChatMessage message = getItem(position);
            message.accept(new IChatMessage.Visitor() {
                @Override
                public void visit(IChatMessage.TextMessage message) {
                    IMessage textMessage = message.getMessage();
                    String targetMessage = getContext().getString(R.string.unknown);
                    boolean selfAuthored;
                    try {
                        selfAuthored = textMessage.getActor() == mService.getSession().getSessionId();
                    } catch (JumbleDisconnectedException e) {
                        selfAuthored = false;
                    }

                    if (textMessage.getTargetChannels() != null && !textMessage.getTargetChannels().isEmpty()) {
                        IChannel currentChannel = (IChannel) textMessage.getTargetChannels().get(0);
                        if (currentChannel != null && currentChannel.getName() != null) {
                            targetMessage = getContext().getString(R.string.chat_message_to, textMessage.getActorName(), currentChannel.getName());
                        }
                    } else if (textMessage.getTargetTrees() != null && !textMessage.getTargetTrees().isEmpty()) {
                        IChannel currentChannel = (IChannel) textMessage.getTargetTrees().get(0);
                        if (currentChannel != null && currentChannel.getName() != null) {
                            targetMessage = getContext().getString(R.string.chat_message_to, textMessage.getActorName(), currentChannel.getName());
                        }
                    } else if (textMessage.getTargetUsers() != null && !textMessage.getTargetUsers().isEmpty()) {
                        User user = (User) textMessage.getTargetUsers().get(0);
                        if (user != null && user.getName() != null) {
                            targetMessage = getContext().getString(R.string.chat_message_to, textMessage.getActorName(), user.getName());
                        }
                    } else {
                        targetMessage = textMessage.getActorName();
                    }

                    int gravity = selfAuthored ? Gravity.RIGHT : Gravity.LEFT;
                    chatBox.setGravity(gravity);
                    messageText.setGravity(gravity);
                    targetText.setText(targetMessage);
                    targetText.setVisibility(View.VISIBLE);
                }

                @Override
                public void visit(IChatMessage.InfoMessage message) {
                    targetText.setVisibility(View.GONE);
                    chatBox.setGravity(Gravity.LEFT);
                    messageText.setGravity(Gravity.LEFT);
                }
            });
            timeText.setText(mDateFormat.format(new Date(message.getReceivedTime())));
            messageText.setText(Html.fromHtml(message.getBody(), mImageGetter, null));
            messageText.setMovementMethod(LinkMovementMethod.getInstance());

            return v;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return false; // Makes links clickable.
        }
    }
}
