package be.anton.curatedphone
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class DraftUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun opensControllerAndNavigatesWithoutGrantingManagement() {
        compose.onNodeWithText("A phone with intention.").assertIsDisplayed()
        compose.onNodeWithText("Apps", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Blocked apps").assertIsDisplayed()
        compose.onNodeWithText("Pair your phone to create a draft.").assertExists()
        compose.onNodeWithText("Websites", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Websites in Chrome").assertIsDisplayed()
        compose.onNodeWithText("Guardians", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your guardians").assertIsDisplayed()
    }
}
