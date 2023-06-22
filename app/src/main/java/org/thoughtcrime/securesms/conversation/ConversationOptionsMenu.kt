/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.text.SpannableString
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.IdRes
import androidx.core.view.MenuProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Delegate object for managing the conversation options menu
 */
internal object ConversationOptionsMenu {

  private val TAG = Log.tag(ConversationOptionsMenu::class.java)

  /**
   * MenuProvider implementation for the conversation options menu.
   */
  class Provider(
    private val callback: Callback,
    private val lifecycleDisposable: LifecycleDisposable
  ) : MenuProvider {

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
      menu.clear()

      val (
        recipient,
        isPushAvailable,
        canShowAsBubble,
        isActiveGroup,
        isActiveV2Group,
        isInActiveGroup,
        hasActiveGroupCall,
        distributionType,
        threadId,
        isInMessageRequest,
        isInBubble
      ) = callback.getSnapshot()

      if (recipient == null) {
        Log.w(TAG, "Recipient is null, no menu")
        return
      }

      if (isInMessageRequest && !recipient.isBlocked) {
        if (isActiveGroup) {
          menuInflater.inflate(R.menu.conversation_message_requests_group, menu)
        }
      }

      if (isPushAvailable) {
        if (recipient.expiresInSeconds > 0) {
          if (!isInActiveGroup) {
            menuInflater.inflate(R.menu.conversation_expiring_on, menu)
          }
          callback.showExpiring(recipient)
        } else {
          if (!isInActiveGroup) {
            menuInflater.inflate(R.menu.conversation_expiring_off, menu)
          }
          callback.clearExpiring()
        }
      }

      if (!recipient.isGroup) {
        if (isPushAvailable) {
          menuInflater.inflate(R.menu.conversation_callable_secure, menu)
        //} else if (!recipient!!.isReleaseNotes && SignalStore.misc().smsExportPhase.allowSmsFeatures()) {
        } else if (!recipient!!.isReleaseNotes) {
          menuInflater.inflate(R.menu.conversation_callable_insecure, menu)
        }
      } else if (recipient.isGroup) {
        if (isActiveV2Group) {
          menuInflater.inflate(R.menu.conversation_callable_groupv2, menu)
          if (hasActiveGroupCall) {
            hideMenuItem(menu, R.id.menu_video_secure)
          }
          callback.showGroupCallingTooltip()
        }
        menuInflater.inflate(R.menu.conversation_group_options, menu)
        if (!recipient.isPushGroup) {
          menuInflater.inflate(R.menu.conversation_mms_group_options, menu)
          if (distributionType == ThreadTable.DistributionTypes.BROADCAST) {
            menu.findItem(R.id.menu_distribution_broadcast).isChecked = true
          } else {
            menu.findItem(R.id.menu_distribution_conversation).isChecked = true
          }
        }
        menuInflater.inflate(R.menu.conversation_active_group_options, menu)
      }

      menuInflater.inflate(R.menu.conversation, menu)

      if (isInMessageRequest && !recipient.isBlocked) {
        hideMenuItem(menu, R.id.menu_conversation_settings)
      }

      if (!recipient.isGroup && !isPushAvailable && !recipient.isReleaseNotes) {
        menuInflater.inflate(R.menu.conversation_insecure, menu)
      }

      if (recipient.isMuted) menuInflater.inflate(R.menu.conversation_muted, menu) else menuInflater.inflate(R.menu.conversation_unmuted, menu)

      if (!recipient.isGroup && (recipient.contactUri == null) && !recipient.isReleaseNotes && !recipient.isSelf && recipient.hasE164()) {
        menuInflater.inflate(R.menu.conversation_add_to_contacts, menu)
      }

      if (recipient.isSelf) {
        if (isPushAvailable) {
          hideMenuItem(menu, R.id.menu_call_secure)
          hideMenuItem(menu, R.id.menu_video_secure)
        } else {
          hideMenuItem(menu, R.id.menu_call_insecure)
        }
        hideMenuItem(menu, R.id.menu_mute_notifications)
      }

      if (recipient.isBlocked) {
        if (isPushAvailable) {
          hideMenuItem(menu, R.id.menu_call_secure)
          hideMenuItem(menu, R.id.menu_video_secure)
          hideMenuItem(menu, R.id.menu_expiring_messages)
          hideMenuItem(menu, R.id.menu_expiring_messages_off)
        } else {
          hideMenuItem(menu, R.id.menu_call_insecure)
        }
        hideMenuItem(menu, R.id.menu_mute_notifications)
      }

      if (recipient.isReleaseNotes) {
        hideMenuItem(menu, R.id.menu_add_shortcut)
      }

      hideMenuItem(menu, R.id.menu_group_recipients)

      if (isActiveV2Group) {
        hideMenuItem(menu, R.id.menu_mute_notifications)
        hideMenuItem(menu, R.id.menu_conversation_settings)
      } else if (recipient.isGroup) {
        hideMenuItem(menu, R.id.menu_conversation_settings)
      }

      hideMenuItem(menu, R.id.menu_create_bubble)
      lifecycleDisposable += canShowAsBubble.subscribeBy(onNext = { yes: Boolean ->
        val item = menu.findItem(R.id.menu_create_bubble)
        if (item != null) {
          item.isVisible = yes && !isInBubble
        }
      })

      if (threadId == -1L) {
        hideMenuItem(menu, R.id.menu_view_media)
      }

      menu.findItem(R.id.menu_format_text_submenu).subMenu?.clearHeader()
      menu.findItem(R.id.edittext_bold).applyTitleSpan(MessageStyler.boldStyle())
      menu.findItem(R.id.edittext_italic).applyTitleSpan(MessageStyler.italicStyle())
      menu.findItem(R.id.edittext_strikethrough).applyTitleSpan(MessageStyler.strikethroughStyle())
      menu.findItem(R.id.edittext_monospace).applyTitleSpan(MessageStyler.monoStyle())

      callback.onOptionsMenuCreated(menu)
    }

    override fun onPrepareMenu(menu: Menu) {
      super.onPrepareMenu(menu)
      val formatText = menu.findItem(R.id.menu_format_text_submenu)
      if (formatText != null) {
        formatText.isVisible = callback.isTextHighlighted()
      }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
      when (menuItem.itemId) {
        R.id.menu_call_secure -> callback.handleDial(true)
        R.id.menu_video_secure -> callback.handleVideo()
        R.id.menu_call_insecure -> callback.handleDial(false)
        R.id.menu_view_media -> callback.handleViewMedia()
        R.id.menu_add_shortcut -> callback.handleAddShortcut()
        R.id.menu_search -> callback.handleSearch()
        R.id.menu_add_to_contacts -> callback.handleAddToContacts()
        R.id.menu_group_recipients -> callback.handleDisplayGroupRecipients()
        R.id.menu_distribution_broadcast -> callback.handleDistributionBroadcastEnabled(menuItem)
        R.id.menu_distribution_conversation -> callback.handleDistributionConversationEnabled(menuItem)
        R.id.menu_group_settings -> callback.handleManageGroup()
        R.id.menu_leave -> callback.handleLeavePushGroup()
        R.id.menu_invite -> callback.handleInviteLink()
        R.id.menu_mute_notifications -> callback.handleMuteNotifications()
        R.id.menu_unmute_notifications -> callback.handleUnmuteNotifications()
        R.id.menu_conversation_settings -> callback.handleConversationSettings()
        R.id.menu_expiring_messages_off, R.id.menu_expiring_messages -> callback.handleSelectMessageExpiration()
        R.id.menu_create_bubble -> callback.handleCreateBubble()
        R.id.home -> callback.handleGoHome()
        R.id.edittext_bold,
        R.id.edittext_italic,
        R.id.edittext_strikethrough,
        R.id.edittext_monospace,
        R.id.edittext_spoiler,
        R.id.edittext_clear_formatting -> callback.handleFormatText(menuItem.itemId)
        else -> return false
      }

      return true
    }

    private fun hideMenuItem(menu: Menu, @IdRes menuItem: Int) {
      if (menu.findItem(menuItem) != null) {
        menu.findItem(menuItem).isVisible = false
      }
    }

    private fun MenuItem.applyTitleSpan(span: Any) {
      title = SpannableString(title).apply { setSpan(span, 0, length, MessageStyler.SPAN_FLAGS) }
    }
  }

  /**
   * Data snapshot for building out menu state.
   */
  data class Snapshot(
    val recipient: Recipient?,
    val isPushAvailable: Boolean,
    val canShowAsBubble: Observable<Boolean>,
    val isActiveGroup: Boolean,
    val isActiveV2Group: Boolean,
    val isInActiveGroup: Boolean,
    val hasActiveGroupCall: Boolean,
    val distributionType: Int,
    val threadId: Long,
    val isInMessageRequest: Boolean,
    val isInBubble: Boolean
  )

  /**
   * Callbacks abstraction for the converstaion options menu
   */
  interface Callback {
    fun getSnapshot(): Snapshot
    fun isTextHighlighted(): Boolean

    fun onOptionsMenuCreated(menu: Menu)

    fun handleVideo()
    fun handleDial(isSecure: Boolean)
    fun handleViewMedia()
    fun handleAddShortcut()
    fun handleSearch()
    fun handleAddToContacts()
    fun handleDisplayGroupRecipients()
    fun handleDistributionBroadcastEnabled(menuItem: MenuItem)
    fun handleDistributionConversationEnabled(menuItem: MenuItem)
    fun handleManageGroup()
    fun handleLeavePushGroup()
    fun handleInviteLink()
    fun handleMuteNotifications()
    fun handleUnmuteNotifications()
    fun handleConversationSettings()
    fun handleSelectMessageExpiration()
    fun handleCreateBubble()
    fun handleGoHome()
    fun showExpiring(recipient: Recipient)
    fun clearExpiring()
    fun showGroupCallingTooltip()
    fun handleFormatText(@IdRes id: Int)
  }
}
