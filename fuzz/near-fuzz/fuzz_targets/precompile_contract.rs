
#[macro_use]
extern crate afl;

use near_vm_runner::VMKind;
use near_vm_runner::logic::{CompiledContract, CompiledContractCache, Config};
use near_vm_runner::ContractCode;
use near_primitives::types::Balance;
use near_primitives::version::PROTOCOL_VERSION;
use near_vm_runner::precompile_contract;
use near_primitives::runtime::apply_state::ApplyState;
use near_primitives::runtime::config::RuntimeConfig;
use near_store::StoreCompiledContractCache;
use std::sync::Arc;
use near_store::test_utils::create_tries;
use near_primitives::runtime::migration_data::{MigrationData, MigrationFlags};
use std::env;
use std::fs::File;
use std::io::Read;

const GAS_PRICE: Balance = 5000;

// libfuzzer_sys::fuzz_target!(|module: ArbitraryModule| {

fn main(){
    fuzz!(|data: &[u8]|{
    let gas_limit = 10u64.pow(15);
    let tries = create_tries();

    let apply_state = ApplyState {
        block_height: 1,
        prev_block_hash: Default::default(),
        block_hash: Default::default(),
        epoch_id: Default::default(),
        epoch_height: 0,
        gas_price: GAS_PRICE,
        block_timestamp: 100,
        gas_limit: Some(gas_limit),
        random_seed: Default::default(),
        current_protocol_version: PROTOCOL_VERSION,
        config: Arc::new(RuntimeConfig::test()),
        cache: Some(Box::new(StoreCompiledContractCache::new(&tries.get_store()))),
        is_new_chunk: true,
        migration_data: Arc::new(MigrationData::default()),
        migration_flags: MigrationFlags::default(),
    };

    // let (runtime, tries, root, apply_state, signer, epoch_info_provider) = setup_runtime(initial_balance, initial_locked, gas_limit);
    // let code = ContractCode::new(module.0.module.to_bytes(), None);
    let wasm = data.to_vec();
    let code = ContractCode::new(wasm, None);

    let vm_kind = apply_state.config.wasm_config.vm_kind; 

    let runtime = vm_kind
        .runtime(apply_state.config.wasm_config.clone())
        .unwrap_or_else(|| panic!("the {vm_kind:?} runtime has not been enabled at compile time"));
   
    let _ = runtime.precompile(&code, (apply_state.cache.as_deref()).unwrap());

    //let _reuslt = precompile_contract(&code, &apply_state.config.wasm_config, PROTOCOL_VERSION, apply_state.cache.as_deref());
    // let _result = run_fuzz(&code, VMKind::for_protocol_version(PROTOCOL_VERSION));
});
}
