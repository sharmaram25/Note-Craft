package com.notecraft.app.data

import com.notecraft.app.data.db.NoteKind

/** First-run content: teaches the app by example instead of an onboarding carousel. */
object WelcomeContent {

    suspend fun seed(repo: NoteRepository) {
        val listId = repo.createNote(title = "Weekend groceries", kind = NoteKind.LIST, color = 4)
        val items = listOf("Sourdough bread", "Avocados", "Coffee beans", "Greek yogurt", "Fresh basil")
        items.forEachIndexed { i, t -> repo.addItem(listId, t, i, checked = i == 2) }
        repo.syncChecklist(listId, "Weekend groceries", repo.getItems(listId))

        repo.createNote(title = "Ideas worth keeping", body = IDEAS, color = 3)
        val welcome = repo.createNote(title = "Welcome to NoteCraft", body = WELCOME)
        repo.setPinned(listOf(welcome), true)
    }

    private val IDEAS = """
        A place for half-formed thoughts. Nothing here has to be perfect.

        - A reading nook by the window
        - Learn to make fresh pasta
        - Weekly *no-phone* morning

        #ideas
    """.trimIndent()

    private val WELCOME = """
        A calm, fast home for everything on your mind. Everything saves as you type — there is no save button.

        ## Write naturally
        Use the toolbar above the keyboard, or plain Markdown:
        - **Bold**, *italic*, ~~strikethrough~~ and ==highlights==
        - `inline code` and > quotes
        - Headings start with #, lists with -

        ## Checklists anywhere
        - [x] Open NoteCraft
        - [ ] Write your first note
        - [ ] Set a reminder in plain words, like "call mom tomorrow 6pm"

        ## Organise without folders
        Add #hashtags anywhere — they become filters on your home screen. Nested tags like #work/projects work too.

        Link notes with double brackets, like [[Ideas worth keeping]]. Tap the link in reading mode to jump there.

        ## Little things
        > Long-press notes to select several. In list view, swipe to pin or delete.

        - Deleted notes wait 30 days in Trash, and every note keeps a version history.
        - Lock private notes with your fingerprint, or lock the whole app in Settings.
        - Add the widget to your home screen, or share text from any app into NoteCraft.

        #welcome
    """.trimIndent()
}
