package com.xiaobai.paycore.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xiaobai.paycore.R
import com.xiaobai.paycore.channel.IPaymentChannel

/**
 * 支付渠道列表适配器（支持选择模式）
 */
class PaymentChannelAdapter(
    private val onChannelSelected: (IPaymentChannel) -> Unit
) : RecyclerView.Adapter<PaymentChannelAdapter.ViewHolder>() {

    private var channels = listOf<IPaymentChannel>()
    private var selectedPosition: Int = -1

    fun submitList(list: List<IPaymentChannel>) {
        channels = list
        notifyDataSetChanged()
    }
    
    /**
     * 获取当前选中的渠道
     */
    fun getSelectedChannel(): IPaymentChannel? {
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

        fun bind(channel: IPaymentChannel, isSelected: Boolean) {
            iconView.setImageResource(channel.channelIcon)
            nameView.text = channel.channelName
            radioButton.isChecked = isSelected

            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = adapterPosition
                
                // 刷新旧的和新的选中项
                if (oldPosition != -1) {
                    notifyItemChanged(oldPosition)
                }
                notifyItemChanged(selectedPosition)
                
                // 通知选择变化
                onChannelSelected(channel)
            }
        }
    }
}