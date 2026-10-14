// SPDX-License-Identifier: GPL-3.0-or-later
/* Android UNICODE collation uses the default ICU collator:
 * https://android.googlesource.com/platform/external/sqlite/+/refs/heads/main/android/sqlite3_android.cpp
 * We register UTF-16: SQLite supplies byte lengths, ICU expects UChar counts.
 * Host ICU version/default locale can differ from an Android device.
 */
#include <sqlite3.h>
#include <unicode/ucol.h>

static int compare_unicode(void *collator,int left_size,const void *left,int right_size,const void *right) {
    return (int)ucol_strcoll((UCollator *)collator,(const UChar *)left,left_size/(int)sizeof(UChar),
                           (const UChar *)right,right_size/(int)sizeof(UChar));
}
static void close_collator(void *collator) { ucol_close((UCollator *)collator); }

int isakai_register_unicode_collation(sqlite3 *database) {
    UErrorCode status=U_ZERO_ERROR;
    UCollator *collator=ucol_open(NULL,&status);
    if(U_FAILURE(status)) { if(collator)ucol_close(collator);return SQLITE_ERROR; }
    int result=sqlite3_create_collation_v2(database,"UNICODE",SQLITE_UTF16,collator,compare_unicode,close_collator);
    /* SQLite does not invoke the destructor when registration fails. */
    if(result!=SQLITE_OK)ucol_close(collator);
    return result;
}
