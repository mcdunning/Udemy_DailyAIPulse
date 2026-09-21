package com.dailyaipulse.sources.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dailyaipulse.core.GeneratedPreview
import com.dailyaipulse.sources.presentation.Source
import com.dailyaipulse.sources.presentation.SourceListUiState

@Composable
fun SourceListContent(
    uiState: SourceListUiState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is SourceListUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("fullScreenLoading")
                )
            }
            is SourceListUiState.Error -> {
                Text(
                    text = uiState.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }
            is SourceListUiState.Success -> {
                if (uiState.sources.isEmpty()) {
                    Text(
                        text = "No sources found",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                } else {
                    LazyColumn {
                        items(uiState.sources) { source ->
                            SourceListItem(source)
                        }
                    }
                }
            }
        }
    }
}

private val previewSource = Source(
    name = "TechCrunch",
    description = "The latest technology news and information on startups, from a sample preview source."
)

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun SourceListContentLoadingPreview() {
    SourceListContent(uiState = SourceListUiState.Loading)
}

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun SourceListContentErrorPreview() {
    SourceListContent(uiState = SourceListUiState.Error("Something went wrong.\nPlease try again."))
}

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun SourceListContentEmptyPreview() {
    SourceListContent(uiState = SourceListUiState.Success(sources = emptyList()))
}

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun SourceListContentSuccessPreview() {
    SourceListContent(
        uiState = SourceListUiState.Success(
            sources = listOf(
                previewSource,
                previewSource.copy(name = "The Verge", description = "Technology, science, art, and culture")
            )
        )
    )
}
