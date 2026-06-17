const { createClient } = require('@supabase/supabase-js');
// require('dotenv').config({ path: '.env.local' });

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://itahjsutfhdsrdhicamt.supabase.co';
const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml0YWhqc3V0Zmhkc3JkaGljYW10Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE0MzE2NjAsImV4cCI6MjA5NzAwNzY2MH0.YvzsLazhipSXXmn7w1x77uLf1SkpH8i-M36cI7sw1H8';

const supabase = createClient(supabaseUrl, supabaseKey);

async function test() {
  console.log("Testing supabase query to guildes...");
  try {
    const { data, error } = await supabase
      .from('guildes')
      .select('id, nom, tag, couleur_hex, avatar_url, chef_id, date_creation, chef:profiles!fk_guildes_chef_id(pseudonyme), profiles:profiles!profiles_guilde_id_fkey(id, total_area_m2)')
      .limit(2);
    
    if (error) {
      console.error("Error with guildes query:", error);
    } else {
      console.log("Guildes query success:", JSON.stringify(data, null, 2));
    }
  } catch (err) {
    console.error(err);
  }

  console.log("\nTesting supabase query to clan_leaderboard joining guildes...");
  try {
    const { data, error } = await supabase
      .from('clan_leaderboard')
      .select('*, guildes:guildes(chef_id, date_creation, chef:profiles!fk_guildes_chef_id(pseudonyme))')
      .limit(2);
    
    if (error) {
      console.error("Error with clan_leaderboard query:", error);
    } else {
      console.log("Clan_leaderboard query success:", JSON.stringify(data, null, 2));
    }
  } catch (err) {
    console.error(err);
  }
}

test();
