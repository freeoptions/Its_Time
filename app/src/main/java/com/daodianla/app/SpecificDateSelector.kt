package com.daodianla.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun SpecificDateSelector(
    selectedDates: List<LocalDate>,
    displayedMonth: YearMonth,
    onDisplayedMonthChange: (YearMonth) -> Unit,
    onDateToggle: (LocalDate) -> Unit,
    errorMessage: String?
) {
    val today = LocalDate.now()
    val selected = selectedDates.toSet()
    val firstDayOffset = displayedMonth.atDay(1).dayOfWeek.value - 1
    val dates = buildList<LocalDate?> {
        repeat(firstDayOffset) { add(null) }
        for (day in 1..displayedMonth.lengthOfMonth()) {
            add(displayedMonth.atDay(day))
        }
        while (size % 7 != 0) add(null)
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "指定日期",
                    color = DaoDianLaColors.ink,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "点击日期可多选，再点一次取消",
                    color = DaoDianLaColors.muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(
                onClick = {
                    onDisplayedMonthChange(
                        (displayedMonth.minusMonths(1)).coerceAtLeast(YearMonth.from(today))
                    )
                },
                enabled = displayedMonth > YearMonth.from(today)
            ) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月")
            }
            Text(
                displayedMonth.year.toString() + "年" + displayedMonth.monthValue + "月",
                color = DaoDianLaColors.ink,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { onDisplayedMonthChange(displayedMonth.plusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = DaoDianLaColors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        dates.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    } else {
                        val isSelected = date in selected
                        val isPast = date < today
                        val isToday = date == today
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (isToday && !isSelected) {
                                        Modifier.border(
                                            BorderStroke(1.dp, DaoDianLaColors.blue),
                                            RoundedCornerShape(12.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .background(
                                    if (isSelected) DaoDianLaColors.blue
                                    else Color.Transparent
                                )
                                .clickable(enabled = !isPast || isSelected) { onDateToggle(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                color = when {
                                    isSelected -> Color.White
                                    isPast -> DaoDianLaColors.muted.copy(alpha = 0.35f)
                                    else -> DaoDianLaColors.ink
                                },
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = if (selectedDates.isEmpty()) {
                "尚未选择日期"
            } else {
                "已选 " + selectedDates.size + " 天：" +
                    selectedDates.sorted().joinToString("、") { formatSelectedDate(it) }
            },
            color = if (errorMessage == null) DaoDianLaColors.muted else DaoDianLaColors.warning,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        errorMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                color = DaoDianLaColors.warning,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

fun formatSelectedDate(date: LocalDate): String =
    date.monthValue.toString() + "月" + date.dayOfMonth + "日"
