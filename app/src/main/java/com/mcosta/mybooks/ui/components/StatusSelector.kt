package com.mcosta.mybooks.ui.components

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mcosta.mybooks.domain.ReadingStatus
import com.mcosta.mybooks.ui.library.label

/** Reading / To read / Read / To buy segmented buttons. */
@Composable
fun StatusSelector(status: ReadingStatus, onChange: (ReadingStatus) -> Unit, modifier: Modifier = Modifier) {
    val options = ReadingStatus.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = status == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(option.label()) },
            )
        }
    }
}
