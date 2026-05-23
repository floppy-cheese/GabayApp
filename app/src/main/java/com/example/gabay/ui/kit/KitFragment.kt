package com.example.gabay.ui.kit

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gabay.databinding.FragmentKitBinding
import com.example.gabay.data.repository.KitRepository

class KitFragment : Fragment() {

    private var _binding: FragmentKitBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: KitViewModel
    private var basicAdapter: KitAdapter? = null
    private var additionalAdapter: KitAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = KitRepository(requireContext())
        val factory = KitViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[KitViewModel::class.java]

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        basicAdapter = KitAdapter { item, isChecked ->
            viewModel.updateItemChecked(item, isChecked)
        }

        additionalAdapter = KitAdapter { item, isChecked ->
            viewModel.updateItemChecked(item, isChecked)
        }

        binding.recyclerBasicKit.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = basicAdapter
            setHasFixedSize(true)
        }

        binding.recyclerAdditionalKit.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = additionalAdapter
            setHasFixedSize(true)
        }

        binding.btnIncrease.setOnClickListener {
            viewModel.increaseHouseholdSize()
        }

        binding.btnDecrease.setOnClickListener {
            viewModel.decreaseHouseholdSize()
        }
    }

    private fun observeViewModel() {
        viewModel.householdSize.observe(viewLifecycleOwner) { size ->
            if (_binding != null) {
                binding.tvHouseholdSize.text = size.toString()
            }
        }

        viewModel.basicItems.observe(viewLifecycleOwner) { items ->
            android.util.Log.d("KitFragment", "Submitting ${items.size} basic items to adapter")
            basicAdapter?.submitList(items)
        }

        viewModel.additionalItems.observe(viewLifecycleOwner) { items ->
            android.util.Log.d("KitFragment", "Submitting ${items.size} additional items to adapter")
            additionalAdapter?.submitList(items)
        }

        viewModel.preparednessStatus.observe(viewLifecycleOwner) { status ->
            if (_binding != null) {
                binding.tvPreparedness.text = status.level

                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f
                    setColor(status.color)
                }
                binding.tvPreparedness.background = drawable
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerBasicKit.adapter = null
        binding.recyclerAdditionalKit.adapter = null
        basicAdapter = null
        additionalAdapter = null
        _binding = null
    }
}