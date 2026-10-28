package net.kuafuai.andee.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Personal-data queries + actuation: contacts, call log, dial, SMS, calendar.
 * ALL runtime permissions, ALL checked per call with graceful denial —
 * personal data is the one place a crash or a silent overreach is
 * unacceptable. Read paths report exactly what they saw; write paths
 * are handed to Android's native editor for the person to explicitly send/save.
 */
class DeviceCommsController(private val context: Context) {

    // ---- contacts ----------------------------------------------------

    fun searchContacts(query: String?, limit: Int = 20): JSONObject {
        if (!ensurePermission(Manifest.permission.READ_CONTACTS)) return denied("contacts")
        val q = query?.trim().orEmpty()
        val selection = if (q.isEmpty()) null
            else ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
        val args = if (q.isEmpty()) null else arrayOf("%$q%")
        val out = JSONArray()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            selection, args,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        )?.use { c: Cursor ->
            var n = 0
            while (c.moveToNext() && n < limit) {
                out.put(JSONObject()
                    .put("name", c.getString(0) ?: "")
                    .put("number", c.getString(1) ?: ""))
                n++
            }
        }
        return JSONObject().put("count", out.length()).put("contacts", out)
    }

    // ---- dial ----------------------------------------------------

    fun dial(number: String, direct: Boolean): JSONObject {
        if (direct) {
            if (!ensurePermission(Manifest.permission.CALL_PHONE)) return denied("call_phone",
                "Direct dialing needs the Phone permission; without it only the dialer can be opened (direct=false).")
            return runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_CALL, Uri.parse("tel:$number"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                JSONObject().put("dialed", number).put("direct", true)
            }.getOrElse { JSONObject().put("error", "call failed: ${it.message}") }
        }
        return runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            JSONObject().put("dialer_opened", number).put("direct", false)
        }.getOrElse { JSONObject().put("error", "dialer failed: ${it.message}") }
    }

    // ---- call log ----------------------------------------------------

    fun callLog(limit: Int = 15): JSONObject {
        if (!ensurePermission(Manifest.permission.READ_CALL_LOG)) return denied("call_log")
        val out = JSONArray()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null, null,
            CallLog.Calls.DATE + " DESC",
        )?.use { c ->
            var n = 0
            while (c.moveToNext() && n < limit) {
                out.put(JSONObject()
                    .put("name", c.getString(0) ?: c.getString(1))
                    .put("number", c.getString(1))
                    .put("type", when (c.getInt(2)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "other"
                    })
                    .put("when", SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(c.getLong(3))))
                    .put("duration_s", c.getLong(4)))
                n++
            }
        }
        return JSONObject().put("count", out.length()).put("calls", out)
    }

    // ---- sms ----------------------------------------------------

    fun readSms(box: String, limit: Int = 15): JSONObject {
        if (!ensurePermission(Manifest.permission.READ_SMS)) return denied("sms")
        val uri = if (box == "sent") Telephony.Sms.Sent.CONTENT_URI else Telephony.Sms.Inbox.CONTENT_URI
        val out = JSONArray()
        context.contentResolver.query(
            uri,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null,
            "date DESC",
        )?.use { c ->
            var n = 0
            while (c.moveToNext() && n < limit) {
                out.put(JSONObject()
                    .put("address", c.getString(0) ?: "")
                    .put("body", (c.getString(1) ?: "").take(300))
                    .put("when", SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(c.getLong(2)))))
                n++
            }
        }
        return JSONObject().put("count", out.length())
            .put("box", box)
            .put("messages", out)
    }

    /** Draft only: user has to press Send in the real messaging application. */
    fun composeSms(number: String, body: String): JSONObject {
        require(number.isNotBlank() && body.isNotBlank()) { "SMS recipient and body are required." }
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                    .putExtra("sms_body", body)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            JSONObject().put("composer_opened", true).put("sent", false)
                .put("to", number)
                .put("requires_user_action", "Review the full message and tap Send in the messaging app.")
        }.getOrElse { JSONObject().put("error", "SMS composer failed to open: ${it.message}") }
    }

    // ---- calendar ----------------------------------------------------

    fun calendarEvents(daysAhead: Int = 3): JSONObject {
        if (!ensurePermission(Manifest.permission.READ_CALENDAR)) return denied("calendar")
        val now = System.currentTimeMillis()
        val until = now + daysAhead * 86_400_000L
        val out = JSONArray()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(until.toString())
            .build()
        context.contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
            ),
            null, null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out.put(JSONObject()
                    .put("title", c.getString(0) ?: "")
                    .put("desc", (c.getString(1) ?: "").take(120))
                    .put("begin", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(c.getLong(2))))
                    .put("end", SimpleDateFormat("HH:mm", Locale.US).format(Date(c.getLong(3))))
                    .put("location", c.getString(4) ?: ""))
            }
        }
        return JSONObject().put("count", out.length()).put("events", out)
    }

    /** Draft only: the calendar provider is not written until the user saves. */
    fun composeCalendarEvent(title: String, beginMs: Long, endMs: Long?, location: String?, description: String?): JSONObject {
        require(title.isNotBlank()) { "Calendar event title is required." }
        require(beginMs > 0L && (endMs == null || endMs > beginMs)) {
            "Calendar event needs a valid start time and a later end time."
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs ?: (beginMs + 3_600_000L))
            location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            description?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }
            context.startActivity(intent)
            JSONObject().put("composer_opened", true).put("added", false)
                .put("requires_user_action", "Review the event and tap Save in the calendar app.")
        }.getOrElse { JSONObject().put("error", "calendar editor failed to open: ${it.message}") }
    }

    // ---- permissions ----------------------------------------------------

    private fun ensurePermission(perm: String): Boolean =
        context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun denied(what: String, hint: String = "Ask the user to grant it once (Settings → Apps → Permissions), then retry.") =
        JSONObject().put("error", "permission denied: $what").put("hint", hint)
}
