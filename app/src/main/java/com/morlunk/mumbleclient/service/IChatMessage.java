/*
 * Copyright (C) 2015 Andrew Comminos
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

package com.morlunk.mumbleclient.service;

import android.os.RemoteException;

import com.morlunk.jumble.model.IMessage;

import java.util.Date;

/**
 * A general chat message, either a text message from a user or an
 * informational notice.
 * Created by andrew on 28/04/15.
 */
public interface IChatMessage {
    /**
     * @return The body of the message.
     */
    String getBody();

    /**
     * @return the unix timestamp when the message was received.
     */
    long getReceivedTime();

    /**
     * Calls the provided visitor object with the proper message implementation.
     * @param visitor A visitor object responding to the underlying chat message type.
     */
    void accept(Visitor visitor);

    /**
     * A text message from a user.
     */
    class TextMessage implements IChatMessage {
        private final IMessage mMessage;

        public TextMessage(IMessage message) {
            mMessage = message;
        }

        public IMessage getMessage() {
            return mMessage;
        }

        @Override
        public String getBody() {
            return mMessage.getMessage();
        }

        @Override
        public long getReceivedTime() {
            return mMessage.getReceivedTime();
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visit(this);
        }
    }

    /**
     * An informational message about the server or client state.
     */
    class InfoMessage implements IChatMessage {
        /**
         * The type of informational message.
         */
        private final Type mType;
        /**
         * The message body.
         */
        private final String mBody;
        /**
         * The time the message was received, as a unix timestamp.
         */
        private final long mReceivedTime;

        public InfoMessage(Type type, String message) {
            mType = type;
            mBody = message;
            mReceivedTime = new Date().getTime();
        }

        public Type getType() {
            return mType;
        }

        @Override
        public String getBody() {
            return mBody;
        }

        @Override
        public long getReceivedTime() {
            return mReceivedTime;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visit(this);
        }

        public enum Type {
            INFO,
            WARNING,
            ERROR
        }
    }

    interface Visitor {
        void visit(TextMessage message);
        void visit(InfoMessage message);
    }
}
