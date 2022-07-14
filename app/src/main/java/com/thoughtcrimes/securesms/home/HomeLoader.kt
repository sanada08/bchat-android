package com.thoughtcrimes.securesms.home

import android.content.Context
import android.database.Cursor
import com.thoughtcrimes.securesms.dependencies.DatabaseComponent
import com.thoughtcrimes.securesms.util.AbstractCursorLoader

class HomeLoader(context: Context) : AbstractCursorLoader(context) {

    override fun getCursor(): Cursor {
        return DatabaseComponent.get(context).threadDatabase().conversationList
    }
}