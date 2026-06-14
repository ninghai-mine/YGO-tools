package com.smallworld.drawer.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallworld.drawer.ui.theme.BgCardHover
import com.smallworld.drawer.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 卡牌图片组件
 * 异步加载卡图文件，加载中/失败时显示占位图
 */
@Composable
fun CardImage(
    imageFile: File?,
    modifier: Modifier = Modifier,
    placeholderText: String = ""
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageFile) {
        bitmap = withContext(Dispatchers.IO) {
            if (imageFile?.exists() == true) {
                BitmapFactory.decodeFile(imageFile.absolutePath)
            } else null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(177f / 254f)
            .clip(RoundedCornerShape(6.dp))
            .background(BgCardHover),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = placeholderText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            // 占位图
            androidx.compose.material3.Text(
                text = if (placeholderText.isEmpty()) "?" else placeholderText.take(2),
                color = TextMuted,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
