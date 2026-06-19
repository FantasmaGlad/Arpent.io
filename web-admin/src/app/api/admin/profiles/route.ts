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
      empireColor, 
      ghostMode, 
      totalAreaM2, 
      allTimeAreaM2, 
      maxAreaM2, 
      areaLostM2, 
      xp, 
      level, 
      loopCount, 
      maxLoopDistanceKm 
    } = await request.json();

    if (!userId) {
      return NextResponse.json({ error: 'User ID is required' }, { status: 400 });
    }

    const updates: any = {};
    if (pseudonyme !== undefined) updates.pseudonyme = pseudonyme;
    if (empireColor !== undefined) updates.empire_color = empireColor;
    if (ghostMode !== undefined) updates.ghost_mode = ghostMode;
    if (totalAreaM2 !== undefined) updates.total_area_m2 = totalAreaM2;
    if (allTimeAreaM2 !== undefined) updates.all_time_area_m2 = allTimeAreaM2;
    if (maxAreaM2 !== undefined) updates.max_area_m2 = maxAreaM2;
    if (areaLostM2 !== undefined) updates.area_lost_m2 = areaLostM2;
    if (xp !== undefined) updates.xp = xp;
    if (level !== undefined) updates.level = level;
    if (loopCount !== undefined) updates.loop_count = loopCount;
    if (maxLoopDistanceKm !== undefined) updates.max_loop_distance_km = maxLoopDistanceKm;

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
  const onlyData = searchParams.get('onlyData') === 'true';

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

    if (onlyData) {
      // Delete game data
      // Delete courses (cascades to points_gps, reactions, comments associated with these courses)
      const { error: coursesError } = await supabaseAdmin
        .from('courses')
        .delete()
        .eq('utilisateur_id', userId);
      if (coursesError) throw coursesError;

      // Delete territoires
      const { error: territoiresError } = await supabaseAdmin
        .from('territoires')
        .delete()
        .eq('utilisateur_id', userId);
      if (territoiresError) throw territoiresError;

      // Delete friendships
      const { error: amisError } = await supabaseAdmin
        .from('amis')
        .delete()
        .or(`demandeur_id.eq.${userId},destinataire_id.eq.${userId}`);
      if (amisError) throw amisError;

      // Delete comments & reactions left by this user on other courses
      await supabaseAdmin.from('course_commentaires').delete().eq('utilisateur_id', userId);
      await supabaseAdmin.from('course_reactions').delete().eq('utilisateur_id', userId);

      // Reset statistics in the profile table
      const { error: profileError } = await supabaseAdmin
        .from('profiles')
        .update({
          total_area_m2: 0.0,
          all_time_area_m2: 0.0,
          max_area_m2: 0.0,
          area_lost_m2: 0.0,
          xp: 0,
          level: 1,
          loop_count: 0,
          max_loop_distance_km: 0.0,
          total_steps: 0,
          average_cadence: 0.0,
          guilde_id: null,
          grade: 'membre',
          avatar_url: null,
          latitude: null,
          longitude: null
        })
        .eq('id', userId);
      if (profileError) throw profileError;
    } else {
      // 2. Delete user from auth.users (which cascades to profiles, courses, territoires, etc.)
      const { error: deleteError } = await supabaseAdmin.auth.admin.deleteUser(userId);
      if (deleteError) {
        return NextResponse.json({ error: deleteError.message }, { status: 400 });
      }
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
