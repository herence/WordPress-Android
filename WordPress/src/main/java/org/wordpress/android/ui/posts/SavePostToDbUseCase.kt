package org.wordpress.android.ui.posts

import android.content.Context
import android.util.Log
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.PostActionBuilder
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.post.PostStatus
import org.wordpress.android.ui.notifications.utils.PendingDraftsNotificationsUtilsWrapper
import org.wordpress.android.ui.uploads.UploadUtilsWrapper
import org.wordpress.android.util.DateTimeUtilsWrapper
import javax.inject.Inject

class SavePostToDbUseCase
@Inject constructor(
    private val uploadUtils: UploadUtilsWrapper,
    private val dateTimeUtils: DateTimeUtilsWrapper,
    private val dispatcher: Dispatcher,
    private val pendingDraftsNotificationsUtils: PendingDraftsNotificationsUtilsWrapper
) {
    fun savePostToDb(
        context: Context,
        postRepository: EditPostRepository,
        site: SiteModel
    ) {
        Log.d("vojta", "Saving post to DB")
        if (postRepository.postHasChangesFromDb()) {
            val post = checkNotNull(postRepository.getEditablePost())
            // mark as pending if the user doesn't have publishing rights
            if (!uploadUtils.userCanPublish(site)) {
                when (postRepository.status) {
                    PostStatus.UNKNOWN,
                    PostStatus.PUBLISHED,
                    PostStatus.SCHEDULED,
                    PostStatus.PRIVATE ->
                        post.setStatus(PostStatus.PENDING.toString())
                    PostStatus.DRAFT,
                    PostStatus.PENDING,
                    PostStatus.TRASHED -> {
                    }
                }
            }
            post.setIsLocallyChanged(true)
            post.setDateLocallyChanged(dateTimeUtils.currentTimeInIso8601UTC())
            handlePendingDraftNotifications(context, postRepository)
            Log.d("vojta", "Post has changes so really saving")
            postRepository.saveDbSnapshot()
            dispatcher.dispatch(PostActionBuilder.newUpdatePostAction(post))
        }
    }

    private fun handlePendingDraftNotifications(
        context: Context,
        editPostRepository: EditPostRepository
    ) {
        if (editPostRepository.status == PostStatus.DRAFT) {
            // now set the pending notification alarm to be triggered in the next day, week, and month
            pendingDraftsNotificationsUtils
                    .scheduleNextNotifications(
                            context,
                            editPostRepository.id,
                            editPostRepository.dateLocallyChanged
                    )
        } else {
            pendingDraftsNotificationsUtils.cancelPendingDraftAlarms(
                    context,
                    editPostRepository.id
            )
        }
    }
}