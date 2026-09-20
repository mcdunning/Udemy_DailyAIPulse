package com.dailyaipulse.articles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dailyaipulse.articles.presentation.Article
import com.dailyaipulse.summary.presentation.SummaryUiState

@Composable
fun ArticleListItem(
    article: Article,
    summaryState: SummaryUiState = SummaryUiState.Idle,
    onSummarize: () -> Unit = {},
    onOpenArticle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenArticle)
    ) {
        Column {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = article.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = article.date,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            SummarySection(summaryState = summaryState, onSummarize = onSummarize)
        }
    }
}

@Composable
private fun SummarySection(summaryState: SummaryUiState, onSummarize: () -> Unit) {
    when (summaryState) {
        is SummaryUiState.Idle -> {
            Button(
                onClick = onSummarize,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Summarize")
            }
        }
        is SummaryUiState.Loading -> {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("summaryLoading")
            )
        }
        is SummaryUiState.Success -> {
            Text(
                text = summaryState.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        is SummaryUiState.Error -> {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = summaryState.message,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onSummarize) {
                    Text("Retry summarize")
                }
            }
        }
    }
}

private val previewArticle = Article(
    title = "Sample Headline About Technology",
    description = "A short sample description of the article content, for preview purposes.",
    imageUrl = null,
    date = "Sep 13, 2026",
    content = "Sample truncated article content for preview purposes...",
    url = "https://example.com/sample-article"
)

@Preview(showBackground = true)
@Composable
private fun ArticleListItemPreview() {
    ArticleListItem(article = previewArticle)
}

@Preview(showBackground = true)
@Composable
private fun ArticleListItemSummaryLoadingPreview() {
    ArticleListItem(article = previewArticle, summaryState = SummaryUiState.Loading)
}

@Preview(showBackground = true)
@Composable
private fun ArticleListItemSummarySuccessPreview() {
    ArticleListItem(
        article = previewArticle,
        summaryState = SummaryUiState.Success(
            "A concise, two-sentence AI-generated summary of the article, shown here for preview purposes."
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun ArticleListItemSummaryErrorPreview() {
    ArticleListItem(
        article = previewArticle,
        summaryState = SummaryUiState.Error("Something went wrong.\nPlease try again.")
    )
}
