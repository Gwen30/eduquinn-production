package zw.co.eduquinn.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import zw.co.eduquinn.app.R

// Single app-wide sans-serif family, replacing the old serif-heading /
// sans-body split (that split was a design flaw flagged during the
// redesign review — a real app shouldn't mix two typefaces for no
// reason). The approved redesign calls for one Arial-style sans
// throughout. Arial itself is Monotype's proprietary font and can't be
// legally bundled into an APK — the bundled file here is Liberation Sans
// (SIL Open Font License 1.1, freely redistributable), which is built
// from the same digitized outline data Google released for its own
// Arimo family (see the font's own copyright metadata), so it reads as
// an identical, metrically Arial-compatible sans. Only Regular + Bold
// ship (res/font/eduquinn_sans_regular.ttf, eduquinn_sans_bold.ttf) —
// Compose's own font synthesis renders the Medium/SemiBold weights below
// by synthesizing from Bold, which is the standard approach for a
// 2-weight family and matches how this looks on the approved mockup.
val EduQuinnFontFamily = FontFamily(
    Font(R.font.eduquinn_sans_regular, FontWeight.Normal),
    Font(R.font.eduquinn_sans_bold, FontWeight.Bold),
)

val EduQuinnTypography = Typography(
    headlineLarge = TextStyle(fontFamily = EduQuinnFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = EduQuinnFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = EduQuinnFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = EduQuinnFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = EduQuinnFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = EduQuinnFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = EduQuinnFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = EduQuinnFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = EduQuinnFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
