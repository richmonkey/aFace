package com.beetle.im;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

import retrofit.http.HEAD;

/**
 * Created by houxh on 14-7-23.
 */

public class IMMessage {
    public long sender;
    public long receiver;
    public int msgLocalID;
    public String content;
}


class Command{
    public static final int MSG_HEARTBEAT = 1;
    public static final int MSG_AUTH = 2;
    public static final int MSG_AUTH_STATUS = 3;
    public static final int MSG_IM = 4;
    public static final int MSG_ACK = 5;
    public static final int MSG_RST = 6;
    public static final int MSG_GROUP_NOTIFICATION = 7;
    public static final int MSG_GROUP_IM = 8;
    public static final int MSG_PEER_ACK = 9;
    public static final int MSG_INPUTTING = 10;
    public static final int MSG_SUBSCRIBE_ONLINE_STATE = 11;
    public static final int MSG_ONLINE_STATE = 12;
    public static final int MSG_PING = 13;
    public static final int MSG_PONG = 14;

    public static final int MSG_VOIP_CONTROL = 64;
    public static final int MSG_VOIP_DATA = 65;
}



class MessagePeerACK {
    public long sender;
    public long receiver;
    public int msgLocalID;
}

class MessageInputing {
    public long sender;
    public long receiver;
}

class MessageOnlineState {
    public long sender;
    public int online;
}

class MessageSubscribe {
    public ArrayList<Long> uids;
}



class Message {

    public static final int HEAD_SIZE = 8;
    public int cmd;
    public int seq;
    public Object body;

    public byte[] pack() {
        int pos = 0;
        byte[] buf = new byte[64*1024];
        BytePacket.writeInt32(seq, buf, pos);
        pos += 4;
        buf[pos] = (byte)cmd;
        pos += 4;

        if (cmd == Command.MSG_HEARTBEAT || cmd == Command.MSG_PING) {
            return Arrays.copyOf(buf, HEAD_SIZE);
        } else if (cmd == Command.MSG_AUTH) {
            BytePacket.writeInt64((Long) body, buf, pos);
            return Arrays.copyOf(buf, HEAD_SIZE+8);
        } else if (cmd == Command.MSG_IM) {
            IMMessage im = (IMMessage) body;
            BytePacket.writeInt64(im.sender, buf, pos);
            pos += 8;
            BytePacket.writeInt64(im.receiver, buf, pos);
            pos += 8;
            BytePacket.writeInt32(im.msgLocalID, buf, pos);
            pos += 4;
            try {
                byte[] c = im.content.getBytes("UTF-8");
                if (c.length + 28 > 64 * 1024) {
                    Log.e("imservice", "packet buffer overflow");
                    return null;
                }
                System.arraycopy(c, 0, buf, pos, c.length);
                return Arrays.copyOf(buf, HEAD_SIZE + 20 + c.length);
            } catch (Exception e) {
                Log.e("imservice", "encode utf8 error");
                return null;
            }
        } else if (cmd == Command.MSG_ACK) {
            BytePacket.writeInt32((Integer)body, buf, pos);
            return Arrays.copyOf(buf, HEAD_SIZE+4);
        } else if (cmd == Command.MSG_SUBSCRIBE_ONLINE_STATE) {
            MessageSubscribe sub = (MessageSubscribe)body;
            BytePacket.writeInt32(sub.uids.size(), buf, pos);
            pos += 4;
            for (int i = 0; i < sub.uids.size(); i++) {
                Long uid = sub.uids.get(i);
                BytePacket.writeInt64(uid, buf, pos);
                pos += 8;
            }
            return Arrays.copyOf(buf, HEAD_SIZE + 4 + sub.uids.size()*8);
        } else if (cmd == Command.MSG_INPUTTING) {
            MessageInputing in = (MessageInputing)body;
            BytePacket.writeInt64(in.sender, buf, pos);
            pos += 8;
            BytePacket.writeInt64(in.receiver, buf, pos);
            return Arrays.copyOf(buf, HEAD_SIZE + 16);
        } else if (cmd == Command.MSG_VOIP_CONTROL) {
            VOIPControl ctl = (VOIPControl)body;
            BytePacket.writeInt64(ctl.sender, buf, pos);
            pos += 8;
            BytePacket.writeInt64(ctl.receiver, buf, pos);
            pos += 8;
            BytePacket.writeInt32(ctl.cmd, buf, pos);
            pos += 4;

            if (ctl.cmd == VOIPControl.VOIP_COMMAND_DIAL) {
                BytePacket.writeInt32(ctl.dialCount, buf, pos);
                pos += 4;
                return Arrays.copyOf(buf, HEAD_SIZE + 24);
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT ||
                    ctl.cmd == VOIPControl.VOIP_COMMAND_CONNECTED) {
                if (ctl.natMap != null) {
                    BytePacket.writeInt32(ctl.natMap.ip, buf, pos);
                    pos += 4;
                    BytePacket.writeInt16(ctl.natMap.port, buf, pos);
                    pos += 2;
                    BytePacket.writeInt32(ctl.natMap.localIP, buf, pos);
                    pos += 4;
                    BytePacket.writeInt16(ctl.natMap.localPort, buf, pos);
                    pos += 2;
                    return Arrays.copyOf(buf, HEAD_SIZE+32);
                } else {
                    return Arrays.copyOf(buf, HEAD_SIZE + 20);
                }
            } else {
                return Arrays.copyOf(buf, HEAD_SIZE + 20);
            }
        }
        return null;
    }

    public boolean unpack(byte[] data) {
        int pos = 0;
        this.seq = BytePacket.readInt32(data, pos);
        pos += 4;
        cmd = data[pos];
        pos += 4;
        if (cmd == Command.MSG_RST) {
            return true;
        } else if (cmd == Command.MSG_AUTH_STATUS) {
            int status = BytePacket.readInt32(data, pos);
            this.body = new Integer(status);
            return true;
        } else if (cmd == Command.MSG_IM) {
            IMMessage im = new IMMessage();
            im.sender = BytePacket.readInt64(data, pos);
            pos += 8;
            im.receiver = BytePacket.readInt64(data, pos);
            pos += 8;
            im.msgLocalID = BytePacket.readInt32(data, pos);
            pos += 4;
            try {
                im.content = new String(data, pos, data.length - 28, "UTF-8");
                this.body = im;
                return true;
            } catch (Exception e) {
                return false;
            }
        } else if (cmd == Command.MSG_ACK) {
            int s = BytePacket.readInt32(data, pos);
            this.body = new Integer(s);
            return true;
        } else if (cmd == Command.MSG_PEER_ACK) {
            MessagePeerACK ack = new MessagePeerACK();
            ack.sender = BytePacket.readInt64(data, pos);
            pos += 8;
            ack.receiver = BytePacket.readInt64(data, pos);
            pos += 8;
            ack.msgLocalID = BytePacket.readInt32(data, pos);
            this.body = ack;
            return true;
        } else if (cmd == Command.MSG_INPUTTING) {
            MessageInputing inputing = new MessageInputing();
            inputing.sender = BytePacket.readInt64(data, pos);
            pos += 8;
            inputing.receiver = BytePacket.readInt64(data, pos);
            this.body = inputing;
            return true;
        } else if (cmd == Command.MSG_ONLINE_STATE) {
            MessageOnlineState state = new MessageOnlineState();
            state.sender = BytePacket.readInt64(data, pos);
            pos += 8;
            state.online = BytePacket.readInt32(data, pos);
            this.body = state;
            return true;
        } else if (cmd == Command.MSG_VOIP_CONTROL) {
            VOIPControl ctl = new VOIPControl();
            ctl.sender = BytePacket.readInt64(data, pos);
            pos += 8;
            ctl.receiver = BytePacket.readInt64(data, pos);
            pos += 8;
            ctl.cmd = BytePacket.readInt32(data, pos);
            pos += 4;
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_DIAL) {
                ctl.dialCount = BytePacket.readInt32(data, pos);
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT ||
                    ctl.cmd == VOIPControl.VOIP_COMMAND_CONNECTED) {
                if (data.length >= HEAD_SIZE + 32) {
                    ctl.natMap = new VOIPControl.NatPortMap();
                    ctl.natMap.ip = BytePacket.readInt32(data, pos);
                    pos += 4;
                    ctl.natMap.port = BytePacket.readInt16(data, pos);
                    pos += 2;
                    ctl.natMap.localIP = BytePacket.readInt32(data, pos);
                    pos += 4;
                    ctl.natMap.localPort = BytePacket.readInt16(data, pos);
                    pos += 2;
                }
            }
            this.body = ctl;
            return true;
        } else if (cmd == Command.MSG_PONG) {
            return true;
        } else {
            return false;
        }
    }
}
