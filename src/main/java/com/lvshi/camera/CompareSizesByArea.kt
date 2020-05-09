package com.lvshi.camera

import android.annotation.TargetApi
import android.util.Size
import java.lang.Long

/**
 * Compare two [Size]s based on their areas.
 */
@TargetApi(21)
class CompareSizesByArea : Comparator<Size> {

    // We cast here to ensure the multiplications won't overflow
    override fun compare(lhs: Size, rhs: Size) =
            Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)


}