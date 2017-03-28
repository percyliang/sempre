#!/usr/bin/env python

"""
SHRDLURN - Community & Logging Server

# Instructions

You can run the server by running ```./server.py --port <PORT_NUMBER>```
"""

import json
import sys
import time
import os
import random
import eventlet
import glob
from optparse import OptionParser
from flask import Flask, request, session
from flask_cors import CORS
from flask_socketio import SocketIO, emit, join_room, leave_room
import jwt
import requests

# Setup flask
app = Flask(__name__)
app.config['SECRET_KEY'] = os.urandom(24)

# We need to enable CORS support to handle CORS flights from the frontend
CORS(app)

# The community server runs through websockets to enable real-time updates
socketio = SocketIO(app)

# Hardcoded folders for the data (mirrored in data_rotate.py)
DATA_FOLDER = "community-server/data/"
LOG_FOLDER = os.path.join(DATA_FOLDER, "log/")
STRUCTS_FOLDER = os.path.join(DATA_FOLDER, "structs/")

CITATION_FOLDER = "../int-output/citation"

# Scoring function parameters
GRAVITY = 1.1  # higher the gravity, the faster old structs lose score
TIME_INTERVAL = 7200.0  # break off by every 30 minutes

# Default port for the server
DEFAULT_PORT = 8406

JWT_SECRET = os.environ['SEMPRE_JWT_SECRET']
SLACK_SECRET = os.environ['SLACK_OAUTH_SECRET']


@app.route("/")
def index():
    return "Hello World! ~ SHRDLURN Community Server"


def is_safe_path(basedir, path, follow_symlinks=True):
    """https://security.openstack.org/guidelines/dg_using-file-paths.html"""
    # resolves symbolic links
    if follow_symlinks:
        return os.path.realpath(path).startswith(os.path.realpath(basedir))

    return os.path.abspath(path).startswith(os.path.abspath(basedir))


def score_struct(timestamp, upvotesN):
    """We use the HN formula to score structures for ranking.

    Formula is: (P + 1) / ((T + 2)^GRAVITY)
    where: - P: the number of unique upvotes for the structure
           - T: the amount of TIME_INTERVALs that have elapsed since the
             structure was submitted
           - GRAVITY: a constant to determine the weight of T v. P
    """
    time_ago = (current_unix_time() / TIME_INTERVAL) - \
        (int(timestamp) / TIME_INTERVAL)
    return (upvotesN + 1) / ((time_ago + 2) ** GRAVITY)


def current_unix_time():
    """Returns the number of seconds since the epoch."""
    return int(time.time())


def emit_structs():
    """Walk through the STRUCTS_FOLDER directory and read each struct and emit
    it to the user one by one."""

    structs = []
    for uid in [name for name in os.listdir(STRUCTS_FOLDER) if os.path.isdir(os.path.join(STRUCTS_FOLDER, name))]:
        uid_folder = os.path.join(STRUCTS_FOLDER, uid)

        count = 0
        for name in os.listdir(uid_folder):
            if count > 100:
                break

            path = os.path.join(uid_folder, name)
            if not os.path.isfile(path):
                continue

            fname = name[:-5]

            try:
                with open(path, 'r') as f:
                    lines = f.readlines()

                    upvotes = json.loads(lines[0].strip())
                    timestamp = json.loads(lines[1].strip())
                    struct = json.loads(lines[2].strip())
                    image = lines[3].strip()

                    score = score_struct(timestamp, len(upvotes))

                    message = {"uid": uid, "id": fname, "score": score, "upvotes": [
                        up for up in upvotes], "struct": struct, "image": image}

                    structs.append(message)
                    count += 1
            except:
                pass

    emit("structs", structs)


def emit_user_structs_count(uid):
    """"Emits a count of the total number of user structs in the folder."""
    path = os.path.join(STRUCTS_FOLDER, uid)
    if not is_safe_path(STRUCTS_FOLDER, path):
        return
        structs = [name[:-5] for name in os.listdir(path) if os.path.isfile(
            os.path.join(path, name)) and os.path.join(path, name).endswith(".json")]
    if os.path.isdir(path):
        structs = [name[:-5] for name in os.listdir(path) if os.path.isfile(
            os.path.join(path, name)) and os.path.join(path, name).endswith(".json")]
        emit("user_structs", {"structs": structs})


def emit_utterances():
    """Emit a list of the last 11 utterances for the 5 most recent turkers."""
    latest_5 = []
    for dirname, subdirs, files in os.walk(LOG_FOLDER):
        for fname in files:
            path = os.path.join(dirname, fname)

            mtime = os.stat(path).st_mtime
            file_info = (mtime, fname[:-5], path)

            if len(latest_5) < 3:
                latest_5.append(file_info)
            else:
                earliest_time = latest_5[0][0]
                earliest_idx = 0
                for idx, l in enumerate(latest_5):
                    if l[0] < earliest_time:
                        earliest_time = l[0]
                        earliest_idx = idx

                if mtime > earliest_time:
                    latest_5[earliest_idx] = file_info

    for (time, uid, path) in sorted(latest_5, key=lambda s: int(s[0]), reverse=True):
        uid = uid
        utts = []
        count = 0
        for line in reverse_readline(path):
            data = json.loads(line)
            if (data["type"] == "accept" or data["type"] == "define"):
                utts.append(line)
                count += 1

            if count > 10:
                break

        message = {"uid": uid, "utterances": utts}
        emit("utterances", message)


def h_index(citations):
    """https://github.com/kamyu104/LeetCode/blob/master/Python/h-index.py"""
    n = len(citations)
    count = [0] * (n + 1)
    for x in citations:
        # Put all x >= n in the same bucket.
        if x >= n:
            count[n] += 1
        else:
            count[x] += 1

    h = 0
    for i in reversed(xrange(0, n + 1)):
        h += count[i]
        if h >= i:
            return i
    return h


def compute_citations(dir):
    citations = []
    for fname in os.listdir(dir):
        if not fname.endswith(".json"):
            continue

        path = os.path.join(dir, fname)

        with open(path, 'r') as f:
            data = json.load(f)
            citations.append(data)

    citation_numbers = [citation["cite"] + citation["self"]
                        for citation in citations]
    citation_score = h_index(citation_numbers)

    return (citations, citation_score)


def emit_top_builders():
    top_5_builders = []
    for uid in os.listdir(CITATION_FOLDER):
        subdir = os.path.join(CITATION_FOLDER, uid)
        if not os.path.isdir(subdir):
            continue

        top_5_builders = sorted(
            top_5_builders, key=lambda b: b[1], reverse=True)

        (citations, citation_score) = compute_citations(subdir)

        top_5_builders = sorted(
            top_5_builders, key=lambda b: b[1], reverse=True)
        if len(top_5_builders) < 10 or citation_score > top_5_builders[9][1]:
            # If there are more than 5 citations with cites, only return those
            citations = sorted(
                citations, key=lambda c: c["cite"] + c["self"], reverse=True)[:10]
            # if len(citations_with_cites) >= 6:
            #     citations = citations_with_cites

            # Sort them by score and return the top 7.
            citations = sorted(
                citations, key=lambda c: c["cite"] + c["self"], reverse=True)[:10]

            struct = (uid, citation_score, citations)
            if len(top_5_builders) < 10:
                top_5_builders.append(struct)
            else:
                top_5_builders[9] = struct

    emit("top_builders", {"top_builders": top_5_builders},
         broadcast=True, room="community")


def log(message):
    """Logs the given message by writing it in the uid's JSON log file."""
    uid = message["uid"] if 'uid' in message else "NULL_session"

    user = current_user(message['token'])
    if user:
        uid = user['id']

    path = os.path.join(LOG_FOLDER, uid + ".json")

    if not is_safe_path(LOG_FOLDER, path):
        print("NOT SAFE!", path)
        return

    # Add a timestamp to the log
    message["timestamp"] = current_unix_time()

    # Append the log to the end of the file
    with open(path, 'a') as f:
        json.dump(message, f)
        f.write('\n')


@socketio.on('getscore')
def get_score(data):
    user = current_user(data['token'])
    if not user:
        return

    uid = user['id']
    subdir = os.path.join(CITATION_FOLDER, uid)
    if (os.path.isdir(subdir) and is_safe_path(CITATION_FOLDER, subdir)):
        (citations, score) = compute_citations(subdir)
        emit("score", {"score": score})


@socketio.on('delete_struct')
def delete_struct(data):
    user = current_user(data['token'])

    if not user:
        return

    uid = user['id']

    struct_id = data["id"]
    struct_path = struct_id + ".json"
    subdir = os.path.join(STRUCTS_FOLDER, uid)
    if not (is_safe_path(STRUCTS_FOLDER, subdir)):
        return
    path = os.path.join(subdir, struct_path)
    if (is_safe_path(subdir, path) and os.path.isfile(path)):
        delete_dir = os.path.join(STRUCTS_FOLDER, uid, "deleted")
        make_dir_if_necessary(delete_dir)
        os.rename(path, os.path.join(delete_dir, struct_path))


@socketio.on('join')
def on_join(data):
    """When a user joins the "community" room, emit to them the list of
    the top 5 most recent users' most recent 11 utterances and all of the
    submitted structs."""

    room = data['room']
    join_room(room)

    if (room == "community"):
        # And then we emit the most recent 5 users' utterances per file
        emit_utterances()

        # and also emit the top builders when first joining
        emit_top_builders()

        # We iterate through all the shared structs and emit them one by one
        emit_structs()


@socketio.on('leave')
def on_leave(data):
    """A user can leave a room"""
    # username = data['sessionId']
    room = data['room']
    leave_room(room)


@socketio.on('share')
def handle_share(data):
    """Users can share structs. We save this struct in STRUCTS_FOLDER/UID/SCORE_ID.json

    where UID is the uid of the user who submitted the struct, SCORE is the
    current score of the struct and ID is the unique index (auto-incremented) of
    this particular struct.."""

    user = current_user(data['token'])
    if not user:
        return

    uid = user['id']

    user_structs_folder = os.path.join(STRUCTS_FOLDER, uid)
    if not is_safe_path(STRUCTS_FOLDER, user_structs_folder):
        return
    make_dir_if_necessary(user_structs_folder)
    new_struct_path = os.path.join(user_structs_folder, data["id"] + ".json")
    if not is_safe_path(user_structs_folder, new_struct_path):
        return

    submission_time = current_unix_time()
    score = score_struct(submission_time, 0)

    with open(new_struct_path, 'w') as f:
        f.write("[]\n")  # it starts with no upvoters
        f.write(str(submission_time) + "\n")  # timestamp of submission
        f.write(json.dumps(data["struct"]) + "\n")  # the actual struct
        f.write(data["image"])  # the png of the struct

    # Broadcast addition to the "community" room
    message = {"uid": uid, "id": data["id"], "score": score, "upvotes": [
    ], "struct": data["struct"], "image": data["image"]}
    emit("struct", message, broadcast=True, room="community")


@socketio.on('upvote')
def upvote(data):
    """Users can upvote other users' structures."""

    user = current_user(data['token'])

    # if no authenticated user, do nothing
    if not user:
        return

    subdir = os.path.join(STRUCTS_FOLDER, data["struct_uid"])
    if not is_safe_path(STRUCTS_FOLDER, subdir):
        return
    struct_path = os.path.join(subdir, str(data["id"]) + ".json")

    # if the struct does not exist, do nothing
    if not (is_safe_path(subdir, struct_path) or os.path.isfile(struct_path)):
        print("not", struct_path)
        return

    # Read the first line of the file to get the number of upvotes
    upvotes = []
    score = 0
    with open(struct_path, 'r+') as f:
        upvotes = json.loads(f.readline().strip())

        # If the user has not already upvoted this, add them
        if user['id'] not in upvotes:
            upvotes.append(user['id'])

            timestamp = f.readline()
            struct = f.readline()
            image = f.readline()

            # reset file to top
            f.seek(0)

            # write file back with updated upvotes
            f.write(json.dumps(upvotes) + "\n")
            f.write(timestamp)  # rewrite the timestamp
            f.write(struct)  # rewrite the actual struct
            f.write(image)  # rewrite the image

            message = {"uid": data["struct_uid"],
                       "id": data["id"], "up": user['id'], "score": score}

            f.truncate()  # truncate to ensure flush appropriate

            # calculate score
            score = score_struct(timestamp, len(upvotes))

            # and then broadcast the new upvote to the room:
            message = {"uid": data["struct_uid"],
                       "id": data["id"], "up": user['id'], "score": score}
            emit("upvote", message, broadcast=True, room="community")


@socketio.on('log')
def handle_log(data):
    """Receive a log message in the form of {"type": LOG_TYPE, "msg": LOG_OBJECT}

    If the log type is an accept of utterance, then broadcast that to all
    community-connected clients."""

    if "type" not in data:
        # If the log object is improper, don't do anything.
        return

    log(data)

    # If the message is an accept or define type, broadcast it to all
    # community-connected clients so they can update their display.
    if data["type"] == "accept":
        emit("new_accept", {"uid": data['uid'], "query": data["msg"]["query"], "timestamp": current_unix_time()},
             broadcast=True, room="community")
    elif data["type"] == "define":
        emit("new_define", {"uid": data['uid'], "defined": data["msg"]["defineAs"], "timestamp": current_unix_time()},
             broadcast=True, room="community")


@socketio.on('session')
def get_session(data):
    """On every new connection, the client should transmit the sessionId to tell
    the server that a new session has started. This sessionId is then used for
    all future authentication by storing it as uid in the session global
    context variable."""
    session.uid = data['uid']
    log({"type": "connect", "token": ""})


@socketio.on('getstructcount')
def getstructcount(data):
    emit_user_structs_count(data['uid'])


@socketio.on('connect')
def connect():
    """Return an ok if connection worked"""
    emit('ok', {'data': 'Connected'})


@socketio.on('disconnect')
def disconnect():
    """Log the fact that a user disconnected."""
    if 'uid' in session:
        log({"uid": session.uid, "type": "disconnect"})


@socketio.on('sign_in')
def sign_in(data):
    r = requests.get("https://slack.com/api/oauth.access", params={
                     'code': data['code'], 'client_id': '130265636855.151294060356', 'client_secret': SLACK_SECRET})
    data = r.json()
    session['access_token'] = data['access_token']
    user = data['user']
    session['user'] = user

    encoded = jwt.encode(
        {'name': user['name'], 'email': user['email'], 'id': user['id']}, JWT_SECRET, algorithm='HS256').decode('utf-8')

    emit('sign_in', {
         "name": user['name'], "email": user['email'], 'id': user['id'], 'token': encoded})


@socketio.on('get_user')
def get_user(data):
    user = current_user(data['token'])
    if user:
        emit('sign_in', {
            "name": user['name'], "email": user['email'], 'id': user['id'], 'token': data['token']
        })
    else:
        emit('sign_in_failed')


def current_user(token):
    try:
        return jwt.decode(token, JWT_SECRET, algorithms=['HS256'])
    except:
        return False


# http://stackoverflow.com/questions/2301789/read-a-file-in-reverse-order-using-python
def reverse_readline(filename, buf_size=8192):
    """a generator that returns the lines of a file in reverse order"""
    with open(filename) as fh:
        segment = None
        offset = 0
        fh.seek(0, os.SEEK_END)
        file_size = remaining_size = fh.tell()
        while remaining_size > 0:
            offset = min(file_size, offset + buf_size)
            fh.seek(file_size - offset)
            buffer = fh.read(min(remaining_size, buf_size))
            remaining_size -= buf_size
            lines = buffer.split('\n')
            # the first line of the buffer is probably not a complete line so
            # we'll save it and append it to the last line of the next buffer
            # we read
            if segment is not None:
                # if the previous chunk starts right from the beginning of line
                # do not concact the segment to the last line of new chunk
                # instead, yield the segment first
                if buffer[-1] is not '\n':
                    lines[-1] += segment
                else:
                    yield segment
            segment = lines[0]
            for index in range(len(lines) - 1, 0, -1):
                if len(lines[index]):
                    yield lines[index]
        # Don't yield None if the file was empty
        if segment is not None:
            yield segment


def make_dir_if_necessary(dir_name):
    """Creates the directory if not already created"""
    if not os.path.exists(dir_name):
        os.makedirs(dir_name)


if __name__ == "__main__":
    # Create any missing directories
    make_dir_if_necessary(DATA_FOLDER)
    make_dir_if_necessary(LOG_FOLDER)
    make_dir_if_necessary(STRUCTS_FOLDER)

    # Parse arguments
    parser = OptionParser()
    parser.add_option("-p", "--port", dest="port",
                      help="port number to run the server", default=DEFAULT_PORT)
    (options, args) = parser.parse_args()

    # Run the server
    # NB: socketio.run uses eventlet to run a production webserver
    # so, make sure that "eventlet" is installed, or else it will default to
    # the werkzeug development server which is unsafe and slow.
    socketio.run(app, host='0.0.0.0', port=int(options.port))
