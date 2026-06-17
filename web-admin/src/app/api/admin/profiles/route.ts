import { NextResponse } from 'next/server';
import { supabaseAdmin } from '@/lib/supabaseAdmin';

// Helper function to check if the caller is the authorized admin
async function verifyAdmin(request: Request) {
  const authHeader = request.headers.get('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return false;
  }
  const token = authHeader.split(' ')[1];
  const { data: { user }, error } = await supabaseAdmin.auth.getUser(token);
  
  if (error || !user) {
    return false;
  }
  
  const { data: adminRecord, error: adminError } = await supabaseAdmin
    .from('admins')
    .select('id')
    .eq('id', user.id)
    .single();

  return !adminError && adminRecord !== null;
}

export async function PUT(request: Request) {
  const isAdmin = await verifyAdmin(request);
  if (!isAdmin) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    const { 
      userId, 
      pseudonyme, 
      avatarUrl, 
      ghost_mode, 
      xp, 
      level, 
      empire_color, 
      total_area_m2, 
      all_time_area_m2, 
      max_area_m2, 
      area_lost_m2, 
      loop_count, 
      max_loop_distance_km, 
      share_location, 
      tag, 
      grade,
      guilde_id
    } = await request.json();

    if (!userId) {
      return NextResponse.json({ error: 'User ID is required' }, { status: 400 });
    }

    const updates: any = {};
    if (pseudonyme !== undefined) updates.pseudonyme = pseudonyme;
    if (ghost_mode !== undefined) updates.ghost_mode = ghost_mode;
    if (xp !== undefined) updates.xp = xp;
    if (level !== undefined) updates.level = level;
    if (empire_color !== undefined) updates.empire_color = empire_color;
    if (total_area_m2 !== undefined) updates.total_area_m2 = total_area_m2;
    if (all_time_area_m2 !== undefined) updates.all_time_area_m2 = all_time_area_m2;
    if (max_area_m2 !== undefined) updates.max_area_m2 = max_area_m2;
    if (area_lost_m2 !== undefined) updates.area_lost_m2 = area_lost_m2;
    if (loop_count !== undefined) updates.loop_count = loop_count;
    if (max_loop_distance_km !== undefined) updates.max_loop_distance_km = max_loop_distance_km;
    if (share_location !== undefined) updates.share_location = share_location;
    if (tag !== undefined) updates.tag = tag;
    if (grade !== undefined) updates.grade = grade === '' ? null : grade;
    if (guilde_id !== undefined) updates.guilde_id = guilde_id === '' ? null : guilde_id;

    if (avatarUrl !== undefined) {
      updates.avatar_url = avatarUrl;
      if (avatarUrl === null) {
        // Fetch old avatar to delete from storage
        const { data: profile } = await supabaseAdmin
          .from('profiles')
          .select('avatar_url')
          .eq('id', userId)
          .single();
        if (profile?.avatar_url) {
          const parts = profile.avatar_url.split('/');
          const fileName = parts[parts.length - 1];
          if (fileName) {
            // Remove cache busting query param if present
            const cleanFileName = fileName.split('?')[0];
            await supabaseAdmin.storage.from('Images').remove([cleanFileName]);
          }
        }
      }
    }

    const { data, error } = await supabaseAdmin
      .from('profiles')
      .update(updates)
      .eq('id', userId)
      .select()
      .single();

    if (error) {
      return NextResponse.json({ error: error.message }, { status: 400 });
    }

    return NextResponse.json({ success: true, profile: data });
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function DELETE(request: Request) {
  const isAdmin = await verifyAdmin(request);
  if (!isAdmin) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { searchParams } = new URL(request.url);
  const userId = searchParams.get('userId');

  if (!userId) {
    return NextResponse.json({ error: 'User ID is required' }, { status: 400 });
  }

  try {
    // 1. Get profile to find if there is an avatar file to clean up
    const { data: profile } = await supabaseAdmin
      .from('profiles')
      .select('avatar_url')
      .eq('id', userId)
      .single();

    // 2. Delete user from auth.users (which cascades to profiles, courses, territoires, etc.)
    const { error: deleteError } = await supabaseAdmin.auth.admin.deleteUser(userId);
    if (deleteError) {
      return NextResponse.json({ error: deleteError.message }, { status: 400 });
    }

    // 3. Clean up avatar in storage if it exists
    if (profile?.avatar_url) {
      // Extract file name from URL (usually ends with <userId>.jpg)
      const parts = profile.avatar_url.split('/');
      const fileName = parts[parts.length - 1];
      if (fileName) {
        const cleanFileName = fileName.split('?')[0];
        await supabaseAdmin.storage.from('Images').remove([cleanFileName]);
      }
    }

    return NextResponse.json({ success: true });
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}
