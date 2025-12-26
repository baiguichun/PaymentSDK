package com.xiaobai.paycore.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xiaobai.paycore.R
import com.xiaobai.paycore.channel.PaymentChannelMeta

/**
 * 支付渠道列表适配器（支持选择模式）
 */
class PaymentChannelAdapter(
    private val onChannelSelected: (PaymentChannelMeta) -> Unit
) : RecyclerView.Adapter<PaymentChannelAdapter.ViewHolder>() {

    private var channels = listOf<PaymentChannelMeta>()
    private var selectedPosition: Int = -1

    fun submitList(list: List<PaymentChannelMeta>) {
        channels = list
        notifyDataSetChanged()
    }
    
    fun getSelectedChannel(): PaymentChannelMeta? {
        return if (selectedPosition >= 0 && selectedPosition < channels.size) {
            channels[selectedPosition]
        } else {
            null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(channels[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = channels.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.iv_channel_icon)
        private val nameView: TextView = itemView.findViewById(R.id.tv_channel_name)
        private val radioButton: RadioButton = itemView.findViewById(R.id.rb_select)

        fun bind(channel: PaymentChannelMeta, isSelected: Boolean) {
            // 后端返回的渠道元数据用于展示，图标加载由宿主自定义（此处清空占位）
            iconView.setImageDrawable(null)
            nameView.text = channel.channelName
            radioButton.isChecked = isSelected

            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = bindingAdapterPosition
                
                if (oldPosition != -1) {
                    notifyItemChanged(oldPosition)
                }
                notifyItemChanged(selectedPosition)
                
                onChannelSelected(channel)
            }
        }
    }
}
